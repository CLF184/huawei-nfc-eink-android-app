package org.nfcresearch.qpolllab;

/** Decides whether to start a fresh attempt after the current backend has exited. */
final class SessionRetry {
    enum Next { STOP, CONTINUE_SEARCH, RETRY }

    private boolean available, waitingForFirstTag;
    private boolean failed, noTag, finished, started, restored, ended, unsafe, targetSeen, d4Sent;
    private String targetUid="";

    SessionRetry(boolean enabled,boolean waitIndefinitely){
        available=enabled;
        waitingForFirstTag=waitIndefinitely;
    }

    void beginAttempt(){
        failed=noTag=finished=started=restored=ended=unsafe=targetSeen=d4Sent=false;
        targetUid="";
    }

    void observe(String kind,String message) {
        switch(kind) {
            case "TAG": waitingForFirstTag=false;break;
            case "TARGET_UID": waitingForFirstTag=false;targetSeen=true;targetUid=message;break;
            case "APDU_BEGIN": d4Sent|=message.startsWith("INS=D4；"); break;
            case "START": started=true; break;
            case "END": ended=true; break;
            case "RESTORED": restored=true; break;
            case "RESTORE_FAILED":
            case "CLEANUP_ERROR":
            case "CANCELLED": unsafe=true; break;
            case "RESULT":
                if(isScanTimeout(message))noTag=true;
                else finished=true;
                break;
            case "READY":
            case "POWER_DONE":
            case "REFRESH_DONE":
            case "REFRESH_UNCONFIRMED": finished=true; break;
            case "ERROR":
                if(message.contains("SecurityException:")||message.contains("NoSuchMethodException:")
                        ||(message.contains("IllegalArgumentException:")&&!message.contains("SW=68CA")
                        &&!message.contains("SW=6502")&&!message.contains("SW=698A")))unsafe=true;
                failed=true;
                break;
            case "QTAG_REJECTED":
            case "PROCESS_EXIT": failed=true; break;
        }
    }

    Next next(String mode,boolean stopped) {
        if(finished||unsafe||stopped)return Next.STOP;
        if(!restored&&!mode.equals("inspect")&&(started||!ended))return Next.STOP;
        if(waitingForFirstTag&&noTag&&!failed)return Next.CONTINUE_SEARCH;
        if(!available||(!failed&&!noTag)||((targetSeen||d4Sent)&&targetUid.isEmpty()))return Next.STOP;
        available=false;
        return Next.RETRY;
    }

    static boolean isScanTimeout(String message){return message.startsWith("本轮超时，未收到标签");}
    void backendUnconfirmed(){unsafe=true;}
    boolean waitingForFirstTag(){return waitingForFirstTag;}
    String targetUid(){return targetUid;}
    boolean statusOnly(){return d4Sent;}

    static void requireSameTarget(String expected,String actual,boolean statusOnly) {
        if((statusOnly&&expected.isEmpty())||(!expected.isEmpty()&&!expected.equals(actual)))
            throw new IllegalArgumentException("自动重试未找到原屏，已停止；未发送 APDU");
    }
}
