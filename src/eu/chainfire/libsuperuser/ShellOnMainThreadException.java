package eu.chainfire.libsuperuser;

/**
 * Exception class used to crash application when shell commands are executed
 * from the main thread, and we are in debug mode. 
 */
@SuppressWarnings("serial")
public class ShellOnMainThreadException extends RuntimeException {
	public ShellOnMainThreadException() {
		super("Application attempted to run a shell command from the main thread");
	}
}
