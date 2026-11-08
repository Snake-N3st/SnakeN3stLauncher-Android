package mc.snakenest.launcher.android.snaken3st;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import java.util.function.Consumer;

/** One system authentication request. No phone credential ever enters the launcher. */
public final class DeviceAuthFragment extends Fragment {
    private static final String TAG="Sn3DeviceAuthentication";
    private Runnable authenticated;
    private Consumer<String> error;
    private boolean started,finished;
    private BiometricPrompt prompt;
    private final ActivityResultLauncher<Intent> credential=registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),result->{
        if(result.getResultCode()==Activity.RESULT_OK)succeed();else fail("Authentification annulée.");
    });
    /** Success callbacks must reference application-owned operations, never an Activity or View. */
    static void authenticate(FragmentActivity host,Runnable authenticated,Consumer<String> error) {
        var manager=host.getSupportFragmentManager();
        if(manager.isStateSaved()){error.accept("Reviens dans le launcher puis réessaie.");return;}
        if(manager.findFragmentByTag(TAG)!=null)return;
        DeviceAuthFragment fragment=new DeviceAuthFragment();fragment.authenticated=authenticated;fragment.error=error;
        manager.beginTransaction().add(fragment,TAG).commitNow();
    }
    @Override public void onCreate(@Nullable Bundle saved) {
        super.onCreate(saved);setRetainInstance(true);
        if(Build.VERSION.SDK_INT>=30)prompt=new BiometricPrompt(this,ContextCompat.getMainExecutor(requireContext()),new BiometricPrompt.AuthenticationCallback(){
            @Override public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result){succeed();}
            @Override public void onAuthenticationError(int code,@NonNull CharSequence text){
                boolean cancelled=code==BiometricPrompt.ERROR_USER_CANCELED||code==BiometricPrompt.ERROR_CANCELED||code==BiometricPrompt.ERROR_NEGATIVE_BUTTON;
                fail(cancelled?"Authentification annulée.":"Authentification Android indisponible : "+text);
            }
        });
        // A process recreation loses the application operation: require a new explicit request.
        if(authenticated==null)finished=true;
    }
    @Override public void onResume() {
        super.onResume();
        if(finished){remove();return;}
        if(started)return;started=true;
        KeyguardManager keyguard=(KeyguardManager)requireContext().getSystemService(Context.KEYGUARD_SERVICE);
        if(keyguard==null||!keyguard.isDeviceSecure()){fail("Configure d’abord un code, un schéma ou un mot de passe de verrouillage dans les paramètres Android.");return;}
        if(Build.VERSION.SDK_INT>=30) {
            int authenticators=BiometricManager.Authenticators.BIOMETRIC_STRONG|BiometricManager.Authenticators.DEVICE_CREDENTIAL;
            if(BiometricManager.from(requireContext()).canAuthenticate(authenticators)!=BiometricManager.BIOMETRIC_SUCCESS){fail("Le déverrouillage Android est momentanément indisponible. Réessaie depuis le launcher.");return;}
            try{prompt.authenticate(new BiometricPrompt.PromptInfo.Builder().setTitle("Déverrouiller Snake N3st")
                .setSubtitle("Utilise ton empreinte ou le verrouillage de ton téléphone")
                .setAllowedAuthenticators(authenticators).build());}
            catch(RuntimeException failure){fail("La demande Android n’a pas pu être affichée. Réessaie depuis le launcher.");}
        }else {
            // API 26–29 cannot combine a strong biometric and device credential in one prompt.
            Intent intent=keyguard.createConfirmDeviceCredentialIntent("Snake N3st","Confirme le verrouillage de ton téléphone");
            if(intent==null){fail("Le déverrouillage Android n’est pas disponible.");return;}
            try{credential.launch(intent);}catch(RuntimeException failure){fail("Le verrouillage Android n’a pas pu être ouvert.");}
        }
    }
    @Override public void onDetach(){error=null;super.onDetach();}
    private void succeed() {
        if(finished)return;finished=true;Runnable action=authenticated;authenticated=null;error=null;
        remove();if(action!=null)action.run();
    }
    private void fail(String message) {
        if(finished)return;finished=true;
        Consumer<String> callback=error;error=null;authenticated=null;
        if(callback!=null)callback.accept(message);
        else if(isAdded()){SnakeRepository repo=SnakeRepository.get(requireContext());repo.error=message;repo.changed();}
        remove();
    }
    private void remove(){if(isAdded())getParentFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();}
}
