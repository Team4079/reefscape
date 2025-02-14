package frc.robot.subsystems;

import static edu.wpi.first.wpilibj.Alert.AlertType.*;
import static frc.robot.utils.Register.Dash.*;
import static frc.robot.utils.RobotParameters.ElevatorParameters.*;
import static frc.robot.utils.RobotParameters.MotorParameters.*;

import com.ctre.phoenix6.configs.*;
import com.ctre.phoenix6.controls.*;
import com.ctre.phoenix6.hardware.*;
import com.ctre.phoenix6.signals.*;
import edu.wpi.first.wpilibj.Alert;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.utils.*;
import frc.robot.utils.RobotParameters.*;
import org.littletonrobotics.junction.networktables.LoggedNetworkNumber;

/**
 * The ElevatorSubsystem class is a Singleton to control the elevator motors on the robot. The class
 * sets the motor positions, gets the motor positions, stops the motors, and toggles the soft stop
 * for the elevator motor.
 */
public class Elevator extends SubsystemBase {
  public static final String ELEVATOR_STATE_KEY = "Elevator State";

  private final TalonFX elevatorMotorLeft;
  private final TalonFX elevatorMotorRight;

  //    private final PositionTorqueCurrentFOC posRequest;
  private final PositionDutyCycle posRequest;
  private final VelocityTorqueCurrentFOC velocityRequest;

  private final SoftwareLimitSwitchConfigs leftSoftLimitConfig;
  private final SoftwareLimitSwitchConfigs rightSoftLimitConfig;

  private LoggedNetworkNumber elevatorP;
  private LoggedNetworkNumber elevatorI;
  private LoggedNetworkNumber elevatorD;
  private LoggedNetworkNumber elevatorV;
  private LoggedNetworkNumber elevatorS;
  private LoggedNetworkNumber elevatorG;
  private LoggedNetworkNumber mm_cruise_velocity;
  private LoggedNetworkNumber mm_acceleration;
  private LoggedNetworkNumber mm_jerk;

  private final VoltageOut voltageOut;
  private TalonFXConfiguration elevatorLeftConfigs;
  private TalonFXConfiguration elevatorRightConfigs;

  private MotionMagicConfigs motionMagicConfigs;

  private Alert elevatorLeftDisconnectedAlert;
  private Alert elevatorRightDisconnectedAlert;

  private ElevatorState currentState = ElevatorState.DEFAULT;

  private final MotionMagicVoltage motionMagicVoltage;

  private final DutyCycleOut cycleOut;

  /**
   * The Singleton instance of this ElevatorSubsystem. Code should use the {@link #getInstance()}
   * method to get the single instance (rather than trying to construct an instance of this class.)
   */
  private static final Elevator INSTANCE = new Elevator();

  /**
   * Returns the Singleton instance of this ElevatorSubsystem. This static method should be used,
   * rather than the constructor, to get the single instance of this class. For example: {@code
   * ElevatorSubsystem.getInstance();}
   */
  @SuppressWarnings("WeakerAccess")
  public static Elevator getInstance() {
    return INSTANCE;
  }

  /**
   * Creates a new instance of this ElevatorSubsystem. This constructor is private since this class
   * is a Singleton. Code should use the {@link #getInstance()} method to get the singleton
   * instance.
   */
  private Elevator() {
    elevatorMotorLeft = new TalonFX(ELEVATOR_MOTOR_LEFT_ID);
    elevatorMotorRight = new TalonFX(ELEVATOR_MOTOR_RIGHT_ID);

    TalonFXConfigurator elevatorLeftConfigurator = elevatorMotorLeft.getConfigurator();
    TalonFXConfigurator elevatorRightConfigurator = elevatorMotorRight.getConfigurator();

    elevatorLeftConfigs = new TalonFXConfiguration();
    elevatorRightConfigs = new TalonFXConfiguration();

    elevatorLeftConfigs.MotorOutput.NeutralMode = NeutralModeValue.Brake;
    elevatorRightConfigs.MotorOutput.NeutralMode = NeutralModeValue.Brake;

    elevatorLeftConfigs.Slot0.kP = ELEVATOR_PIDV.getP();
    elevatorLeftConfigs.Slot0.kI = ELEVATOR_PIDV.getI();
    elevatorLeftConfigs.Slot0.kD = ELEVATOR_PIDV.getD();
    elevatorLeftConfigs.Slot0.kV = ELEVATOR_PIDV.getV();
    elevatorLeftConfigs.Slot0.kS = ELEVATOR_S;
    elevatorLeftConfigs.Slot0.kG = ELEVATOR_G;

    elevatorRightConfigs.Slot0.kP = ELEVATOR_PIDV.getP();
    elevatorRightConfigs.Slot0.kI = ELEVATOR_PIDV.getI();
    elevatorRightConfigs.Slot0.kD = ELEVATOR_PIDV.getD();
    elevatorRightConfigs.Slot0.kV = ELEVATOR_PIDV.getV();
    elevatorRightConfigs.Slot0.kS = ELEVATOR_S;
    elevatorRightConfigs.Slot0.kG = ELEVATOR_G;

    elevatorMotorLeft.getConfigurator().apply(elevatorLeftConfigs);
    elevatorMotorRight.getConfigurator().apply(elevatorRightConfigs);

    CurrentLimitsConfigs leftMotorCurrentConfig = new CurrentLimitsConfigs();
    CurrentLimitsConfigs rightMotorCurrentConfig = new CurrentLimitsConfigs();

    ClosedLoopRampsConfigs leftMotorRampConfig = new ClosedLoopRampsConfigs();
    ClosedLoopRampsConfigs rightMotorRampConfig = new ClosedLoopRampsConfigs();

    leftSoftLimitConfig = new SoftwareLimitSwitchConfigs();
    rightSoftLimitConfig = new SoftwareLimitSwitchConfigs();

    leftMotorCurrentConfig.SupplyCurrentLimit = 100;
    leftMotorCurrentConfig.SupplyCurrentLimitEnable = true;
    leftMotorCurrentConfig.StatorCurrentLimit = 100;
    leftMotorCurrentConfig.StatorCurrentLimitEnable = true;

    rightMotorCurrentConfig.SupplyCurrentLimit = 100;
    rightMotorCurrentConfig.SupplyCurrentLimitEnable = true;
    rightMotorCurrentConfig.StatorCurrentLimit = 100;
    rightMotorCurrentConfig.StatorCurrentLimitEnable = true;

    elevatorMotorLeft.getConfigurator().apply(leftMotorCurrentConfig);
    elevatorMotorRight.getConfigurator().apply(rightMotorCurrentConfig);

    leftMotorRampConfig.DutyCycleClosedLoopRampPeriod = 0.1;
    rightMotorRampConfig.DutyCycleClosedLoopRampPeriod = 0.1;

    elevatorMotorLeft.getConfigurator().apply(leftMotorRampConfig);
    elevatorMotorRight.getConfigurator().apply(rightMotorRampConfig);

    // on
    leftSoftLimitConfig.ForwardSoftLimitEnable = true;
    leftSoftLimitConfig.ReverseSoftLimitEnable = true;
    leftSoftLimitConfig.ForwardSoftLimitThreshold = ELEVATOR_SOFT_LIMIT_UP;
    leftSoftLimitConfig.ReverseSoftLimitThreshold = ELEVATOR_SOFT_LIMIT_DOWN;

    rightSoftLimitConfig.ForwardSoftLimitEnable = true;
    rightSoftLimitConfig.ReverseSoftLimitEnable = true;
    rightSoftLimitConfig.ReverseSoftLimitThreshold = ELEVATOR_SOFT_LIMIT_DOWN;
    rightSoftLimitConfig.ForwardSoftLimitThreshold = ELEVATOR_SOFT_LIMIT_UP;

    elevatorLeftConfigs.MotorOutput.Inverted = InvertedValue.CounterClockwise_Positive;
    elevatorRightConfigs.MotorOutput.Inverted = InvertedValue.Clockwise_Positive;
    elevatorLeftConfigs.SoftwareLimitSwitch = leftSoftLimitConfig;
    elevatorRightConfigs.SoftwareLimitSwitch = rightSoftLimitConfig;

    elevatorMotorLeft.getConfigurator().apply(leftSoftLimitConfig);
    elevatorMotorRight.getConfigurator().apply(rightSoftLimitConfig);

    velocityRequest = new VelocityTorqueCurrentFOC(0);
    posRequest = new PositionDutyCycle(0);
    voltageOut = new VoltageOut(0);
    motionMagicVoltage = new MotionMagicVoltage(0);
    cycleOut = new DutyCycleOut(0);

    //TODO THESE NEED TO BE LOGGED
    motionMagicConfigs = elevatorLeftConfigs.MotionMagic;
    motionMagicConfigs.MotionMagicCruiseVelocity = ELEVATOR_MM_CRUISE_VELOCITY;
    motionMagicConfigs.MotionMagicAcceleration = ELEVATOR_MM_ACCELERATION;
    motionMagicConfigs.MotionMagicJerk = ELEVATOR_MM_JERK;

    motionMagicVoltage.Slot = 0;
//    motionMagicVoltage.EnableFOC = true;
//    motionMagicVoltage.FeedForward = 0;

    velocityRequest.OverrideCoastDurNeutral = false;

    voltageOut.OverrideBrakeDurNeutral = false;
    voltageOut.EnableFOC = true;

    cycleOut.EnableFOC = false;

    elevatorMotorLeft.setPosition(0);
    elevatorMotorRight.setPosition(0);

    elevatorLeftConfigurator.apply(elevatorLeftConfigs);
    elevatorRightConfigurator.apply(elevatorRightConfigs);

    elevatorLeftConfigurator.apply(motionMagicConfigs);
    elevatorRightConfigurator.apply(motionMagicConfigs);

    elevatorLeftDisconnectedAlert =
        new Alert("Disconnected left elevator motor " + ELEVATOR_MOTOR_LEFT_ID, kError);
    elevatorRightDisconnectedAlert =
        new Alert("Disconnected right elevator motor " + ELEVATOR_MOTOR_RIGHT_ID, kError);

    initizalizeLoggedNetworkPID();
  }

  // This method will be called once per scheduler run
  @Override
  public void periodic() {
    elevatorLeftDisconnectedAlert.set(!elevatorMotorLeft.isConnected());
    elevatorRightDisconnectedAlert.set(!elevatorMotorRight.isConnected());

    moveElevatorToLevel();

    logs(
        () -> {
          log("Elevator Left Position", elevatorMotorLeft.getPosition().getValueAsDouble());
          log("Elevator Right Position", elevatorMotorRight.getPosition().getValueAsDouble());
          log("Elevator Left Set Speed", elevatorMotorLeft.getVelocity().getValueAsDouble());
          log("Elevator Right Set Speed", elevatorMotorRight.getVelocity().getValueAsDouble());
          log("Elevator Left Acceleration", elevatorMotorLeft.getAcceleration().getValueAsDouble());
          log("Elevator Right Acceleration", elevatorMotorRight.getAcceleration().getValueAsDouble());
          log("/Elevator/Elevator supply voltage", elevatorMotorLeft.getSupplyVoltage().getValueAsDouble());
          log("/Elevator/Elevator motor voltage", elevatorMotorLeft.getMotorVoltage().getValueAsDouble());
          log(ELEVATOR_STATE_KEY, currentState.toString());
          log(
              "Disconnected elevatorMotorLeft" + elevatorMotorLeft.getDeviceID(),
              elevatorMotorLeft.isConnected());
          log(
              "Disconnected elevatorMotorRight" + elevatorMotorRight.getDeviceID(),
              elevatorMotorRight.isConnected());
          log(
              "Disconnected elevatorMotorLeft" + elevatorMotorLeft.getDeviceID(),
              elevatorMotorLeft.isConnected());
          log(
              "Disconnected elevatorMotorRight" + elevatorMotorRight.getDeviceID(),
              elevatorMotorRight.isConnected());
        });
  }

  /** Move the elevator motor to a specific level */
  public void moveElevatorToLevel() {
    switch (this.currentState) {
      case L1:
        setElevatorPosition(ElevatorParameters.L1);
        break;
      case L2:
        setElevatorPosition(ElevatorParameters.L2);
        break;
      case L3:
        setElevatorPosition(ElevatorParameters.L3);
        break;
      case L4:
        setElevatorPosition(ElevatorParameters.L4);
        break;
      default:
        setElevatorPosition(ElevatorParameters.DEFAULT);
        break;
    }
  }

  /** Stops the elevator motors */
  public void stopMotors() {
    elevatorMotorLeft.stopMotor();
    elevatorMotorRight.stopMotor();
    voltageOut.Output = -0.014;
    elevatorMotorLeft.setControl(voltageOut);
    elevatorMotorRight.setControl(voltageOut);
  }

  /** Sets the elevator state */
  public void setState(ElevatorState currentState) {
    this.currentState = currentState;
  }

  /**
   * Get the state of the elevator motor
   *
   * @return ElevatorState, the state of the elevator motor
   */
  public ElevatorState getState() {
    return this.currentState;
  }

  /**
   * Gets the state of the elevator motor as a double in terms of its height in the parameters file
   *
   * @return double, the state of the elevator motor as a double
   */
  public double getStateDouble() {
    return switch (this.currentState) {
      case L1 -> ElevatorParameters.L1;
      case L2 -> ElevatorParameters.L2;
      case L3 -> ElevatorParameters.L3;
      case L4 -> ElevatorParameters.L4;
      default -> ElevatorParameters.DEFAULT;
    };
  }

  /**
   * Get the position of the elevator motor
   *
   * @param motor "left" or "right | The motor to get the position of
   * @return double, the position of the elevator motor and -1.0 if the motor is not "left" or
   *     "right"
   */
  public double getElevatorPosValue(String motor) {
    if (motor.equalsIgnoreCase("left")) {
      return elevatorMotorLeft.getPosition().getValueAsDouble();
    } else if (motor.equalsIgnoreCase("right")) {
      return elevatorMotorRight.getPosition().getValueAsDouble();
    } else {
      // This returns if the motor is not "left" or "right"
      System.out.println(
          "getElevatorPosValue: Invalid motor type, motor type should only be 'left' or 'right'");
      return -1.0;
    }
  }

  /**
   * Get the average position of the elevator motors
   *
   * @return double, the average position of the elevator motors
   */
  public double getElevatorPosAvg() {
    return (this.getElevatorPosValue("left") + this.getElevatorPosValue("right")) / 2;
  }

  /** Soft resets the encoders on the elevator motors */
  public void resetEncoders() {
    elevatorMotorLeft.setPosition(0);
    elevatorMotorRight.setPosition(0);
  }

  /** Toggles the soft stop for the elevator motor */
  public void toggleSoftStop() {
    ElevatorParameters.isSoftLimitEnabled = !ElevatorParameters.isSoftLimitEnabled;
    leftSoftLimitConfig.ReverseSoftLimitEnable = ElevatorParameters.isSoftLimitEnabled;
    leftSoftLimitConfig.ReverseSoftLimitThreshold = 0;

    rightSoftLimitConfig.ReverseSoftLimitEnable = ElevatorParameters.isSoftLimitEnabled;
    rightSoftLimitConfig.ReverseSoftLimitThreshold = 0;

    elevatorMotorLeft.getConfigurator().apply(leftSoftLimitConfig);
    elevatorMotorRight.getConfigurator().apply(rightSoftLimitConfig);
  }

  /**
   * Move the elevator motor at a specific velocity
   *
   * @param speed double, the velocity to move the elevator motor at
   */
  public void moveElevator(double speed) {
    final double deadband = 0.001;
    double velocity = -speed * 0.3309;
    if (Math.abs(velocity) >= deadband) {
      // TODO THESE MAY BE NEGATIVE DO NOT MURDER THE MOTORS SOFTWARE PLS!!!!!!!!!!!!!!!!!!!!!!!
//      elevatorMotorLeft.set(velocity);
//      elevatorMotorRight.set(velocity);
        elevatorMotorLeft.setControl(cycleOut.withOutput(velocity));
        elevatorMotorRight.setControl(cycleOut.withOutput(velocity));
    } else {
      stopMotors();
    }
  }

  /**
   * Sets the elevator motor to a specific position
   *
   * @param pos double, the position to set the elevator motor to
   */
  public void setElevatorPosition(double pos) {
    elevatorMotorLeft.setControl(motionMagicVoltage.withPosition(pos));
    elevatorMotorRight.setControl(motionMagicVoltage.withPosition(pos));
  }

  public void initizalizeLoggedNetworkPID() {
    elevatorP = new LoggedNetworkNumber("/Tuning/Elevator/Elevator P", elevatorRightConfigs.Slot0.kP);
    elevatorI = new LoggedNetworkNumber("/Tuning/Elevator/Elevator I", elevatorRightConfigs.Slot0.kI);
    elevatorD = new LoggedNetworkNumber("/Tuning/Elevator/Elevator D", elevatorRightConfigs.Slot0.kD);
    elevatorV = new LoggedNetworkNumber("/Tuning/Elevator/Elevator V", elevatorRightConfigs.Slot0.kV);
    elevatorS = new LoggedNetworkNumber("/Tuning/Elevator/Elevator S", elevatorRightConfigs.Slot0.kS);
    elevatorG = new LoggedNetworkNumber("/Tuning/Elevator/Elevator G", elevatorRightConfigs.Slot0.kG);

    mm_cruise_velocity = new LoggedNetworkNumber("/Tuning/Elevator/MM Cruise Velocity", motionMagicConfigs.MotionMagicCruiseVelocity);
    mm_acceleration = new LoggedNetworkNumber("/Tuning/Elevator/MM Acceleration", motionMagicConfigs.MotionMagicAcceleration);
    mm_jerk = new LoggedNetworkNumber("/Tuning/Elevator/MM Jerk", motionMagicConfigs.MotionMagicJerk);
  }

  public void updateElevatorPID() {
    ELEVATOR_PIDV.setP(elevatorP.get());
    ELEVATOR_PIDV.setI(elevatorI.get());
    ELEVATOR_PIDV.setD(elevatorD.get());
    ELEVATOR_PIDV.setV(elevatorV.get());
    ELEVATOR_S = elevatorS.get();
    ELEVATOR_G = elevatorG.get();

    ELEVATOR_MM_CRUISE_VELOCITY = mm_cruise_velocity.get();
    ELEVATOR_MM_ACCELERATION = mm_acceleration.get();
    ELEVATOR_MM_JERK = mm_jerk.get();

    applyElevatorPIDValues();
  }

  public void applyElevatorPIDValues() {
    elevatorLeftConfigs.Slot0.kP = elevatorP.get();
    elevatorLeftConfigs.Slot0.kI = elevatorI.get();
    elevatorLeftConfigs.Slot0.kD = elevatorD.get();
    elevatorLeftConfigs.Slot0.kV = elevatorV.get();
    elevatorLeftConfigs.Slot0.kS = elevatorS.get();
    elevatorLeftConfigs.Slot0.kG = elevatorG.get();

    elevatorRightConfigs.Slot0.kP = elevatorP.get();
    elevatorRightConfigs.Slot0.kI = elevatorI.get();
    elevatorRightConfigs.Slot0.kD = elevatorD.get();
    elevatorRightConfigs.Slot0.kV = elevatorV.get();
    elevatorRightConfigs.Slot0.kS = elevatorS.get();
    elevatorRightConfigs.Slot0.kG = elevatorG.get();

    motionMagicConfigs = elevatorLeftConfigs.MotionMagic;
    motionMagicConfigs.MotionMagicCruiseVelocity = mm_cruise_velocity.get();
    motionMagicConfigs.MotionMagicAcceleration = mm_acceleration.get();
    motionMagicConfigs.MotionMagicJerk = mm_jerk.get();

    elevatorMotorLeft.getConfigurator().apply(elevatorLeftConfigs);
    elevatorMotorRight.getConfigurator().apply(elevatorRightConfigs);

    elevatorMotorLeft.getConfigurator().apply(motionMagicConfigs);
    elevatorMotorRight.getConfigurator().apply(motionMagicConfigs);
  }
}
