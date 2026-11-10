package mc.snakenest.launcher.android.snaken3st;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.inputmethod.BaseInputConnection;
import mc.snakenest.launcher.android.LauncherActivity;
import mc.snakenest.launcher.android.customcontrols.keyboard.CharacterSenderStrategy;
import mc.snakenest.launcher.android.customcontrols.keyboard.TouchCharInput;

/** Replays IME composition through the actual text watcher, without launching a game. */
final class KeyboardInputChecks {
    static void run(Instrumentation test) {
        Bundle result=new Bundle(); int[] passed={0};
        try {
            var target=test.getTargetContext();
            int theme=target.getPackageManager().getActivityInfo(new ComponentName(target,LauncherActivity.class),0).getThemeResource();
            test.runOnMainSync(()->{
                var input=new TouchCharInput(new ContextThemeWrapper(target,theme));
                var sender=new RecordingSender(); input.setCharacterSender(sender);
                var connection=new BaseInputConnection(input,true) {
                    @Override public android.text.Editable getEditable() { return input.getEditableText(); }
                };
                // Ctrl held; '/' opened the chat and therefore is absent from the IME buffer.
                sender.ctrl=true; sender.game.append('/');
                connection.setComposingText("t",1);
                check(sender.game.toString().equals("/t"),"first letter");passed[0]++;
                connection.setComposingText("ti",1);
                check(sender.game.toString().equals("/ti")&&sender.backspaces==0,"third character preserves slash");passed[0]++;
                connection.setComposingText("time",1);
                connection.commitText("time",1);
                check(sender.game.toString().equals("/time")&&sender.backspaces==0,"commit does not erase or duplicate command");passed[0]++;
                connection.commitText(" set day",1);
                check(sender.game.toString().equals("/time set day"),"arguments");passed[0]++;
                // The IME may also include the slash in the composing range itself.
                input.clear();sender.reset();sender.ctrl=true;
                connection.setComposingText("/t",1);connection.setComposingText("/ti",1);connection.commitText("/time",1);
                check(sender.game.toString().equals("/time")&&sender.backspaces==0,"slash within composing word");passed[0]++;
                // Identical full-buffer recommits must be no-ops, including the hidden filler.
                String same=input.getText().toString();input.getEditableText().replace(0,input.length(),same);
                check(sender.game.toString().equals("/time")&&sender.backspaces==0,"identical full-buffer replacement");passed[0]++;
                // Normal correction still replaces the differing tail, and Backspace still works.
                input.clear();sender.reset();
                connection.setComposingText("cat",1);connection.setComposingText("car",1);connection.commitText("car",1);
                check(sender.game.toString().equals("car")&&sender.backspaces==1,"correction preserves prefix");passed[0]++;
                connection.deleteSurroundingText(1,0);
                check(sender.game.toString().equals("ca")&&sender.backspaces==2,"ordinary backspace");passed[0]++;
                // Filler represents characters already entered directly in the game.
                input.clear();sender.reset();sender.game.append('/');connection.deleteSurroundingText(1,0);
                check(sender.game.length()==0&&sender.backspaces==1,"backspace into pre-existing game text");passed[0]++;
                input.clear();sender.reset();connection.commitText("été / été",1);
                check(sender.game.toString().equals("été / été"),"accents and slash");passed[0]++;
            });
            result.putBoolean("success",true);
        } catch(Throwable failure) { result.putBoolean("success",false);result.putString("message",failure.toString()); }
        result.putInt("checks",passed[0]);test.finish(result.getBoolean("success")?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private static void check(boolean success,String label) { if(!success)throw new AssertionError(label); }
    private static final class RecordingSender implements CharacterSenderStrategy {
        final StringBuilder game=new StringBuilder(); boolean ctrl; int backspaces;
        @Override public void sendChar(char character) {game.append(character);}
        @Override public void sendEnter() { }
        @Override public void sendBackspace() {
            backspaces++;
            if(ctrl) game.setLength(0); // Legacy Ctrl+Backspace can eat the command prefix.
            else if(game.length()>0)game.deleteCharAt(game.length()-1);
        }
        void reset() {game.setLength(0);ctrl=false;backspaces=0;}
    }
}
