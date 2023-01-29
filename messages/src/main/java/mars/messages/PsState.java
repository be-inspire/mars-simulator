package mars.messages;

public enum PsState {
	INIT, ASSIGNED, REQUESTED, IN_TRANSIT_TO_ASSIGNED, IN_TRANSIT_TO_OLD_ASSIGNED, PLATFORM_ENGAGED, LANDED, ENDED,
	ABORTING, ABORTED, WAITING_LANDING_PERMISSION, READY_TO_SCHEDULE, IN_PROGRESS, LANDING_AUTHORIZED,
	READY_TO_LIFT_OFF, LIFT_OFF, FAILED_PS, CLEARING_CYLINDER
}