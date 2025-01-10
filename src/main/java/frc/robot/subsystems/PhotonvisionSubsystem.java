package frc.robot.subsystems;

import edu.wpi.first.apriltag.*;
import edu.wpi.first.math.geometry.*;
import edu.wpi.first.wpilibj.*;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.utils.*;
import java.util.*;
import org.littletonrobotics.junction.Logger;
import org.photonvision.*;
import org.photonvision.targeting.*;

/**
 * The PhotonvisionSubsystem class is a subsystem that interfaces with the PhotonVision system to
 * provide vision tracking and pose estimation capabilities. This subsystem is a Singleton, meaning
 * that only one instance of this class is created and shared across the entire robot code.
 *
 * <p>This subsystem provides methods to get the estimated global pose of the robot, the distance to
 * the subwoofer, and the yaw of the subwoofer. It also provides methods to check if a tag is
 * visible, get the forward distance to the target, and get the pivot position.
 */
public class PhotonvisionSubsystem extends SubsystemBase {
  // PhotonVision cameras
  private final PhotonCamera camera = new PhotonCamera("Camera");

  // Pose estimator for determining the robot's position on the field
  private final PhotonPoseEstimator photonPoseEstimator;

  private final Translation2d cameraTrans = new Translation2d(0.31, 0.0);

  // AprilTag field layout for the 2024 Crescendo field
  private AprilTagFieldLayout aprilTagFieldLayout =
      AprilTagFieldLayout.loadField(AprilTagFields.k2024Crescendo);

  // Transformation from the robot to the camera
  private Transform3d cameraPos =
      new Transform3d(
          Extensions.dimensionIncrease(
              cameraTrans, RobotParameters.PhotonVisionConstants.CAMERA_ONE_HEIGHT_METER),
          new Rotation3d(
              0.0,
              Math.toRadians(360 - RobotParameters.PhotonVisionConstants.CAMERA_ONE_ANGLE_DEG),
              Math.toRadians(180.0)));

  private PhotonTrackedTarget target = null;
  // private boolean isTargetVisible = false;
  private double yaw = -15.0;
  private double targetPoseAmbiguity = 7157.0;
  private double rangeToTarget = 0.0;
  private List<PhotonPipelineResult> result;
  private PhotonPipelineResult currentResult = null;
  // private boolean camTag = false;
  private Translation3d currentPose = null;

  /**
   * The Singleton instance of this PhotonvisionSubsystem. Code should use the {@link
   * #getInstance()} method to get the single instance (rather than trying to construct an instance
   * of this class.)
   */
  private static final PhotonvisionSubsystem INSTANCE = new PhotonvisionSubsystem();

  /**
   * Returns the Singleton instance of this PhotonvisionSubsystem. This static method should be
   * used, rather than the constructor, to get the single instance of this class. For example:
   * {@code PhotonvisionSubsystem.getInstance();}
   */
  @SuppressWarnings("WeakerAccess")
  public static PhotonvisionSubsystem getInstance() {
    return INSTANCE;
  }

  /**
   * Creates a new instance of this PhotonvisionSubsystem. This constructor is private since this
   * class is a Singleton. Code should use the {@link #getInstance()} method to get the singleton
   * instance.
   */
  private PhotonvisionSubsystem() {
    result = camera.getAllUnreadResults();
    photonPoseEstimator =
        new PhotonPoseEstimator(
            aprilTagFieldLayout,
            PhotonPoseEstimator.PoseStrategy.MULTI_TAG_PNP_ON_COPROCESSOR,
            cameraPos);
  }

  /**
   * This method is called periodically by the scheduler. It updates the tracked targets and
   * displays relevant information on the SmartDashboard.
   */
  @Override
  public void periodic() {
    result = camera.getAllUnreadResults();
    currentResult = result.isEmpty() ? null : result.get(0);

    if (currentResult == null) return;

    photonPoseEstimator.update(currentResult);

    target = currentResult.getBestTarget();
    targetPoseAmbiguity = target != null ? target.getPoseAmbiguity() : 7157.0;

    for (PhotonTrackedTarget tag : currentResult.getTargets()) {
        yaw = tag.getYaw();
    }

    Logger.recordOutput("yaw to target", yaw);
    Logger.recordOutput("range target", rangeToTarget);
    Logger.recordOutput("april tag yaw", getSubwooferYaw());
    Logger.recordOutput("cam ambiguity", targetPoseAmbiguity);
    Logger.recordOutput("_targets", currentResult.hasTargets());
  }

  /**
   * Checks if there is a tag.
   *
   * <p>This method is useful to avoid NullPointerExceptions when trying to access specific info
   * based on vision.
   *
   * @return true if there is a tag, false otherwise.
   */
  public boolean hasTag() {
    return currentResult != null && currentResult.hasTargets();
  }

  /**
   * Gets the estimated global pose of the robot.
   *
   * @param prevEstimatedRobotPose The previous estimated pose of the robot.
   * @return The estimated robot pose, or null if no pose could be estimated.
   */
  public EstimatedRobotPose getEstimatedGlobalPose(Pose2d prevEstimatedRobotPose) {
    photonPoseEstimator.setReferencePose(prevEstimatedRobotPose);
    return currentResult != null ? photonPoseEstimator.update(currentResult).orElse(null) : null;
  }

  /**
   * Gets the estimated global pose of the robot.
   *
   * @return The estimated global pose of the robot.
   */
  @SuppressWarnings("java:S3655") // It does call Optional.isPresent :rolleyes:
  public Transform3d getEstimatedGlobalPose() {
    return currentResult != null && currentResult.getMultiTagResult().isPresent()
        ? currentResult.getMultiTagResult().get().estimatedPose.best
        : new Transform3d(0.0, 0.0, 0.0, new Rotation3d());
  }

  /**
   * Uses some fancy stuff to return the distance from the april tag
   * @return double
   */
  public double getDistanceAprilTag() {
    return Math.sqrt(
        Math.pow(getEstimatedGlobalPose().getTranslation().getX(), 2)
            + Math.pow(getEstimatedGlobalPose().getTranslation().getY(), 2));
  }

  /**
   * Gets the forward distance to the target.
   *
   * @return The forward distance to the target.
   */
  public double getPivotPosition() {
    // 10/14/2024 outside tuning
    // Desmos: https://www.desmos.com/calculator/naalukjxze
    double r = getDistanceAprilTag() + 0.6;
    double f = -1.39223; // power 5
    double e = 20.9711; // power 4
    double d = -122.485; // power 3
    double c = 342.783; // power 2
    double b = -447.743; // power 1
    double a = 230.409; // constant

    return (f * Math.pow(r, 5.0))
        + (e * Math.pow(r, 4.0))
        + (d * Math.pow(r, 3.0))
        + (c * Math.pow(r, 2.0))
        + (b * r)
        + a;
  }

  /**
   * Gets the yaw of the subwoofer.
   *
   * @return The yaw of the subwoofer.
   */
  public double getSubwooferYaw() {
    return 180 - Math.toDegrees(getEstimatedGlobalPose().getRotation().getAngle());
  }

  /**
   * Gets the yaw value.
   *
   * @return The current yaw value.
   */
  public double getYaw() {
    return yaw;
  }

  /**
   * Sets the yaw value.
   *
   * @param yaw The new yaw value to set.
   */
  public void setYaw(double yaw) {
    this.yaw = yaw;
  }
}
