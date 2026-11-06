package org.nfcresearch.qpolllab;

public final class SessionFailureTest {
    private static void require(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args){
        SessionFailure f=new SessionFailure();
        f.accept("ERROR","IOException: D1 失败，SW=6985");
        f.accept("PROCESS_EXIT","1");
        require(f.message().contains("SW=6985")&&f.message().contains("退出码=1"));
        f.accept("RESTORE_FAILED","恢复未确认，请重启 NFC");
        require(f.message().contains("SW=6985")&&f.message().contains("恢复未确认"));
        f.clear();require(!f.present());
        f.accept("QTAG_REJECTED","系统拒绝 QTag，响应状态=05");
        f.accept("ERROR","IllegalStateException: 系统拒绝 QTag，响应状态=05");
        f.accept("ERROR","后端进程退出码=1");
        require(f.message().startsWith("系统拒绝 QTag，响应状态=05")&&f.message().contains("退出码=1"));
        f.clear();f.accept("PROCESS_EXIT","137");
        require(f.message().contains("未收到具体错误")&&f.message().contains("137"));
        f.clear();f.accept("ERROR","屏幕操作失败");
        f.accept("ERROR","IOException: D1 失败，SW=6401");
        f.accept("ERROR","IOException: D1 失败，SW=6401");
        f.accept("ERROR","刷新查询失败，SW=68CA");
        f.accept("PROCESS_EXIT","1");f.accept("RESTORE_FAILED","NFC 恢复未确认");
        String complete=f.message();
        require(complete.contains("屏幕操作失败")&&complete.contains("SW=6401")&&complete.contains("SW=68CA")&&complete.contains("退出码=1")&&complete.contains("恢复未确认"));
        require(complete.indexOf("SW=6401")==complete.lastIndexOf("SW=6401"));
        retryCases();
        System.out.println("SessionFailure regression tests passed");
    }
    private static void retryCases(){
        for(String mode:new String[]{"inspect","standard","qtag","identity","refresh","power"}){
            SessionRetry retry=new SessionRetry(true,false);
            retry.observe("ERROR","IOException: connection lost");
            require(retry.next(mode,false)==(mode.equals("inspect")?SessionRetry.Next.RETRY:SessionRetry.Next.STOP));
            if(!mode.equals("inspect")){
                retry.observe("RESTORED","");
                require(retry.next(mode,false)==SessionRetry.Next.RETRY);
            }
            retry.beginAttempt();
            retry.observe("ERROR","IOException: still lost");
            retry.observe("RESTORED","");
            require(retry.next(mode,false)==SessionRetry.Next.STOP);
        }
        SessionRetry d4=new SessionRetry(true,false);
        d4.observe("TARGET_UID","0102");
        d4.observe("APDU_BEGIN","INS=D4；超时=8000 ms");
        d4.observe("ERROR","TagLostException: Tag was lost");
        d4.observe("RESTORED","");
        require(d4.next("refresh",true)==SessionRetry.Next.STOP);
        require(d4.next("refresh",false)==SessionRetry.Next.RETRY&&d4.statusOnly()&&d4.targetUid().equals("0102"));
        SessionRetry.requireSameTarget("0102","0102",true);
        for(String expected:new String[]{"","0102"}){
            try{SessionRetry.requireSameTarget(expected,"0304",true);throw new AssertionError("different target accepted");}
            catch(IllegalArgumentException rejected){}
        }
        for(String kind:new String[]{"RESTORE_FAILED","CLEANUP_ERROR","CANCELLED","REFRESH_DONE","REFRESH_UNCONFIRMED"}){
            SessionRetry blocked=new SessionRetry(true,false);
            blocked.observe("RESTORED","");
            blocked.observe("ERROR","IOException: lost tag");
            blocked.observe(kind,"");
            require(blocked.next("refresh",false)==SessionRetry.Next.STOP);
        }
        SessionRetry rejected=new SessionRetry(true,false);
        rejected.observe("ERROR","IllegalArgumentException: D1 规格不匹配");
        rejected.observe("RESTORED","");
        require(rejected.next("refresh",false)==SessionRetry.Next.STOP);
        SessionRetry lostBackend=new SessionRetry(true,false);
        lostBackend.observe("RESTORED","");
        lostBackend.observe("ERROR","IOException: backend disconnected");
        lostBackend.backendUnconfirmed();
        require(lostBackend.next("refresh",false)==SessionRetry.Next.STOP);
        SessionRetry waiting=new SessionRetry(false,true);
        for(int window=0;window<3;window++){
            waiting.beginAttempt();
            waiting.observe("START","refresh:60");
            waiting.observe("RESULT","本轮超时，未收到标签；不能据此认定硬件不支持");
            require(waiting.next("refresh",false)==SessionRetry.Next.STOP);
            waiting.observe("RESTORED","");
            require(waiting.next("refresh",true)==SessionRetry.Next.STOP);
            require(waiting.next("refresh",false)==SessionRetry.Next.CONTINUE_SEARCH);
        }
        waiting.beginAttempt();
        waiting.observe("TAG","first tag");
        waiting.observe("ERROR","TagLostException: Tag was lost");
        waiting.observe("RESTORED","");
        require(!waiting.waitingForFirstTag()&&waiting.next("refresh",false)==SessionRetry.Next.STOP);
    }
}
