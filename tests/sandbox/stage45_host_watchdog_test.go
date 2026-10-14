package main

import (
 "crypto/ed25519"
 "crypto/rand"
 "encoding/hex"
 "fmt"
 "strings"
 "testing"
 "time"
)

func signedBeat(t *testing.T, key ed25519.PrivateKey, worker, job, container,
 state string, epoch,seq uint64, issued, expires int64) []byte {
 t.Helper()
 data:=fmt.Sprintf("%s|%s|%s|%s|%d|%d|%d|%d|%s\n",
  domain,worker,job,container,epoch,seq,issued,expires,state)
 return []byte(data+hex.EncodeToString(ed25519.Sign(key,[]byte(data)))+"\n")
}

func TestAuthenticatedHeartbeatBindsWorkerJobContainerAndEpoch(t *testing.T) {
 pub,priv,err:=ed25519.GenerateKey(rand.Reader)
 if err!=nil {t.Fatal(err)}
 now:=time.Now().Unix()
 worker:="workerA"
 job:="11111111-1111-1111-1111-111111111111"
 container:=strings.Repeat("a",64)
 msg:=signedBeat(t,priv,worker,job,container,"ACTIVE",1,1,now,now+3)
 if p,e:=verifyHeartbeat(pub,msg,worker,job,container,1,0,now);e!=nil||p.state!="ACTIVE" {
  t.Fatalf("trusted ACTIVE heartbeat rejected: %v",e)
 }
 for _,wrong:=range []struct{worker,job,container string}{
  {"wrong",job,container},{worker,"22222222-2222-2222-2222-222222222222",container},
  {worker,job,strings.Repeat("b",64)},
 } {
  if _,e:=verifyHeartbeat(pub,msg,wrong.worker,wrong.job,wrong.container,1,0,now);e==nil {
   t.Fatal("foreign identity accepted")
  }
 }
 stop:=signedBeat(t,priv,worker,job,container,"STOP",2,2,now,now+3)
 if p,e:=verifyHeartbeat(pub,stop,worker,job,container,1,1,now);e!=nil||p.state!="STOP" {
  t.Fatalf("authenticated higher-epoch STOP rejected: %v",e)
 }
}

func TestTamperedReplayExpiredAndFutureHeartbeatFailClosed(t *testing.T) {
 pub,priv,_:=ed25519.GenerateKey(rand.Reader)
 wrongPub,_,_:=ed25519.GenerateKey(rand.Reader)
 now:=time.Now().Unix()
 worker,job,container:="workerA","11111111-1111-1111-1111-111111111111",strings.Repeat("a",64)
 original:=signedBeat(t,priv,worker,job,container,"ACTIVE",1,2,now,now+3)
 if _,err:=verifyHeartbeat(wrongPub,original,worker,job,container,1,0,now);err==nil {
  t.Fatal("wrong public key accepted")
 }
 tampered:=append([]byte(nil),original...)
 tampered[10]^=1
 if _,err:=verifyHeartbeat(pub,tampered,worker,job,container,1,0,now);err==nil {
  t.Fatal("modified signed fields accepted")
 }
 if _,err:=verifyHeartbeat(pub,original,worker,job,container,1,3,now);err==nil {
  t.Fatal("replayed older sequence accepted")
 }
 if _,err:=verifyHeartbeat(pub,original,worker,job,container,1,0,now+4);err==nil {
  t.Fatal("expired heartbeat accepted")
 }
 if _,err:=verifyHeartbeat(pub,original,worker,job,container,1,0,now-1);err==nil {
  t.Fatal("future issued heartbeat accepted")
 }
 stopSameEpoch:=signedBeat(t,priv,worker,job,container,"STOP",1,3,now,now+3)
 if _,err:=verifyHeartbeat(pub,stopSameEpoch,worker,job,container,1,2,now);err==nil {
  t.Fatal("same epoch STOP accepted")
 }
 long:=signedBeat(t,priv,worker,job,container,"ACTIVE",1,4,now,now+30)
 if _,err:=verifyHeartbeat(pub,long,worker,job,container,1,2,now);err==nil {
  t.Fatal("unbounded lease accepted")
 }
}
