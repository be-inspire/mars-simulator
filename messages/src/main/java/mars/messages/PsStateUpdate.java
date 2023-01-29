package mars.messages;

public record PsStateUpdate(int psId, PsState previous, PsState current) {

}
