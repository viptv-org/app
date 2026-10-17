import java.nio.file.*;
import java.util.*;
import kotlin.Unit;
import kotlin.coroutines.*;
import kotlinx.coroutines.*;
import org.viptv.app.connection.*;
import org.viptv.app.state.*;
import uniffi.viptv_core.SecretStore;

public class DesktopVaultHarness {
  static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
  static <T> T block(kotlin.jvm.functions.Function2<CoroutineScope,Continuation<? super T>,Object> action) throws Exception {
    return BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE,action);
  }
  static void output(String value) { System.out.println(value); System.out.flush(); }
  public static void main(String[] args) throws Exception {
    long start=System.nanoTime();
    SecretStore vault=DesktopSecretsKt.platformSecrets();
    String nativeOverride=System.getProperty("uniffi.component.viptv_core.libraryOverride");
    if(nativeOverride!=null) {
      require(Files.readAllLines(Path.of("/proc/self/maps")).stream().anyMatch(line->line.endsWith(nativeOverride)),"Rebuilt native override not loaded");
      output("rebuilt_native_mapped:true");
      output("rebuilt_native_sha256:"+java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(nativeOverride)))));
    }
    NativeRemoteSessionStore store=new NativeRemoteSessionStore();
    String phase=args[0];
    if(phase.equals("write-local")) {
      block((scope,c)->store.clear(c));
      require(block((scope,c)->store.savedConnection(c))==SavedConnection.None.INSTANCE,"Expected empty private store");
      block((scope,c)->store.rememberLocalConnection(c));
      require(block((scope,c)->store.savedConnection(c))==SavedConnection.Local.INSTANCE,"Local marker missing");
      output("local_write_read_passed");
    } else if(phase.equals("read-local")) {
      require(block((scope,c)->store.savedConnection(c))==SavedConnection.Local.INSTANCE,"Fresh process local restore missing");
      output("local_fresh_process_read_passed");
    } else if(phase.equals("legacy-remote")) {
      vault.put("remote-session-v1","{\"origin\":\"https://owned.invalid\",\"refreshToken\":\"owned-not-a-real-token\"}");
      SavedConnection saved=block((scope,c)->store.savedConnection(c));
      require(saved instanceof SavedConnection.Remote,"Legacy remote JSON unreadable");
      SavedRemoteSession remote=((SavedConnection.Remote)saved).getSession();
      require(remote.getOrigin().equals("https://owned.invalid")&&remote.getRefreshToken().equals("owned-not-a-real-token"),"Legacy JSON values changed");
      block((scope,c)->store.save(new SavedRemoteSession("https://rotated-owned.invalid","owned-rotated-not-a-real-token"),c));
      SavedRemoteSession read=block((scope,c)->store.read(c));
      require(read.getOrigin().equals("https://rotated-owned.invalid"),"Remote replacement failed");
      block((scope,c)->store.rememberLocalConnection(c));
      require(block((scope,c)->store.savedConnection(c))==SavedConnection.Local.INSTANCE,"Remote to local atomic replacement failed");
      block((scope,c)->store.clear(c)); block((scope,c)->store.clear(c));
      require(block((scope,c)->store.savedConnection(c))==SavedConnection.None.INSTANCE,"Idempotent clear failed");
      output("legacy_remote_save_read_replace_clear_passed");
    } else if(phase.equals("read")) {
      output("native_vault_call_started");
      try { block((scope,c)->store.savedConnection(c)); output("vault_call_succeeded"); }
      catch(Exception error) { output("vault_call_failed:"+error.getClass().getName()); }
    } else if(phase.equals("credential-matching")) {
      require("owned-legacy".equals(vault.get("qa-legacy")),"No-target legacy item unreadable");
      vault.put("qa-legacy","owned-replaced");
      require("owned-replaced".equals(vault.get("qa-legacy")),"Legacy item replacement failed");
      vault.remove("qa-legacy");vault.remove("qa-legacy");
      require(vault.get("qa-legacy")==null,"Legacy clear failed");
      require("owned-exact".equals(vault.get("qa-priority")),"Exact credential did not override legacy");
      vault.remove("qa-priority");
      require("owned-fallback".equals(vault.get("qa-priority")),"Legacy fallback lost after exact deletion");
      vault.remove("qa-priority");
      int rejected=0;
      try {vault.get("qa-ambiguous");}catch(Exception expected){require(expected instanceof uniffi.viptv_core.CoreException.Bridge,"Unexpected failure");rejected++;}
      try {vault.put("qa-ambiguous","must-not-replace");}catch(Exception expected){require(expected instanceof uniffi.viptv_core.CoreException.Bridge,"Unexpected failure");rejected++;}
      try {vault.remove("qa-ambiguous");}catch(Exception expected){require(expected instanceof uniffi.viptv_core.CoreException.Bridge,"Unexpected failure");rejected++;}
      require(rejected==3,"Ambiguous credentials accepted");
      output("legacy_identity_exact_priority_ambiguous_rejection_passed");
    } else if(phase.equals("locked-access")) {
      int failures=0;
      long operation=System.nanoTime();
      try {vault.get("remote-session-v1");}catch(Exception error){require(error instanceof uniffi.viptv_core.CoreException.Bridge,"Unexpected get failure");failures++;}
      output("get_ms:"+((System.nanoTime()-operation)/1000000));operation=System.nanoTime();
      try {vault.put("remote-session-v1","must-not-write");}catch(Exception error){require(error instanceof uniffi.viptv_core.CoreException.Bridge,"Unexpected put failure");failures++;}
      output("put_ms:"+((System.nanoTime()-operation)/1000000));operation=System.nanoTime();
      try {vault.remove("remote-session-v1");}catch(Exception error){require(error instanceof uniffi.viptv_core.CoreException.Bridge,"Unexpected delete failure");failures++;}
      output("delete_ms:"+((System.nanoTime()-operation)/1000000));
      require(failures==3,"Locked/unconfigured vault operation unexpectedly succeeded");output("vault_access_failures:"+failures);
    } else if(phase.equals("cancel-startup")) {
      java.util.concurrent.atomic.AtomicInteger opened=new java.util.concurrent.atomic.AtomicInteger();
      CoroutineScope scope=CoroutineScopeKt.CoroutineScope(Dispatchers.getDefault().plus(JobKt.Job((Job)null)));
      ApplicationController controller=new ApplicationController(scope,store,null,
        path->{opened.incrementAndGet();return NativeLocalConnectionKt.localConnection(path);},
        (origin,saved)->{throw new AssertionError("Canceled vault read opened remote transport");});
      ControllerSessionKt.restore(controller,System.getenv("XDG_DATA_HOME")+"/owned.sqlite");
      Thread.sleep(1000);Job restoring=controller.getJob$VIPTV_composeApp();
      output("startup_vault_wait_active:"+controller.getState().getValue().getBusy());
      output("startup_vault_notice:"+(controller.getState().getValue().getSessionNotice()!=null));
      output("startup_vault_error_present:"+(controller.getState().getValue().getError()!=null));
      long cancelStart=System.nanoTime();ControllerSessionKt.disconnect(controller);
      output("disconnect_ms:"+((System.nanoTime()-cancelStart)/1000000));
      output("disconnect_screen:"+controller.getState().getValue().getScreen());
      output("disconnect_starting:"+controller.getState().getValue().getStarting());
      output("profiles_immediately_after_cancel:"+controller.getState().getValue().getProfiles().size());
      Thread.sleep(400);output("canceled_restore_job_completed:"+restoring.isCompleted());
      ControllerSessionKt.useLocal(controller,System.getenv("XDG_DATA_HOME")+"/owned.sqlite");
      Thread.sleep(1200);
      output("new_local_choice_busy:"+controller.getState().getValue().getBusy());
      output("new_local_choice_factory_calls:"+opened.get());
      output("private_profiles_after_cancel:"+controller.getState().getValue().getProfiles().size());
      output("new_local_choice_screen:"+controller.getState().getValue().getScreen());
      output("new_local_choice_notice:"+(controller.getState().getValue().getSessionNotice()!=null));
      ControllerSessionKt.disconnect(controller);CoroutineScopeKt.cancel(scope,(java.util.concurrent.CancellationException)null);
    }
    output("elapsed_ms:"+((System.nanoTime()-start)/1000000));
  }
}
