package android.os;

/** Desktop stub of android.os.Process: JVM process identity. */
public class Process {
    public static int myPid() {
        return (int) java.lang.ProcessHandle.current().pid();
    }

    public static int myUid() {
        return 10000;
    }

    public static int myTid() {
        return (int) Thread.currentThread().getId();
    }
}
