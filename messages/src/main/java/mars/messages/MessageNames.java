package mars.messages;

public interface MessageNames {

	public String PS_DEMAND_REQUEST = "psDemandRequest";
	public String PS_ABORT_INDICATION = "psAbortIndication";
	public String PLATFORM_STATUS_INDICATION = "platformStatusIndication";
	public String PLATFORM_REACHABILITY_INDICATION = "platformReachabilityIndication";
	public String PLATFORM_ASSIGNMENT_INDICATION = "platformAssignmentIndication";
	public String PLATFORM_AVAILABILITY_INDICATION = "platformAvailabilityIndication";

	public String READY_PS_REQUEST = "readyPsRequest";

	public String PLATFORM_STATUS_REQUEST = "platformStatusRequest";
	public String PLATFORM_QUIT_REQUEST = "platformQuitRequest";
	public String PLATFORM_CYLINDER_ENTER_REQUEST = "platformCylinderEnterRequest";

	public String DRONE_LANDED_INDICATION = "droneLandedIndication";
	public String PLATFORM_CYLINDER_LEFT_INDICATION = "platformCylinderLeftIndication";

	public String PS_COMPLETED_INDICATION = "psCompletedIndication";

	public String ANOMALY_INDICATION = "anomalyIndication";
}
