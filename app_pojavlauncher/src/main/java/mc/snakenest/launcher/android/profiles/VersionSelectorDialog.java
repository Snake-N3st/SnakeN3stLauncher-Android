package mc.snakenest.launcher.android.profiles;

import static mc.snakenest.launcher.android.extra.ExtraCore.getValue;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.ExpandableListView;

import androidx.appcompat.app.AlertDialog;

import mc.snakenest.launcher.android.JMinecraftVersionList;
import mc.snakenest.launcher.android.R;
import mc.snakenest.launcher.android.extra.ExtraConstants;

public class VersionSelectorDialog {
    public static void open(Context context, boolean hideCustomVersions, VersionSelectorListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        ExpandableListView expandableListView = (ExpandableListView) LayoutInflater.from(context)
                .inflate(R.layout.dialog_expendable_list_view , null);
        JMinecraftVersionList jMinecraftVersionList = (JMinecraftVersionList) getValue(ExtraConstants.RELEASE_TABLE);
        JMinecraftVersionList.Version[] versionArray;
        if(jMinecraftVersionList == null || jMinecraftVersionList.versions == null) versionArray = new JMinecraftVersionList.Version[0];
        else versionArray = jMinecraftVersionList.versions;
        VersionListAdapter adapter = new VersionListAdapter(versionArray, hideCustomVersions, context);

        expandableListView.setAdapter(adapter);
        builder.setView(expandableListView);
        AlertDialog dialog = builder.show();

        expandableListView.setOnChildClickListener((parent, v1, groupPosition, childPosition, id) -> {
            String version = adapter.getChild(groupPosition, childPosition);
            listener.onVersionSelected(version, adapter.isSnapshotSelected(groupPosition));
            dialog.dismiss();
            return true;
        });
    }
}
