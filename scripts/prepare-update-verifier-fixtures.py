#!/usr/bin/env python3
"""Prepare tiny signed archive-verifier fixtures; never installs an APK.

Requires a built debug APK, Android SDK build tools, a platform android.jar and
keytool. All packaging/signing processes run with nice(19). The throwaway signer
is for tests only; the valid signer is the repository's public debug keystore.
"""
from pathlib import Path
import subprocess
import argparse, os, re
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--apk',required=True,type=Path)
parser.add_argument('--aapt',required=True,type=Path)
parser.add_argument('--apksigner',required=True,type=Path)
parser.add_argument('--android-jar',required=True,type=Path)
parser.add_argument('--output',required=True,type=Path)
args=parser.parse_args()
r=Path(__file__).resolve().parents[1]
out=args.output.resolve();out.mkdir(parents=True,exist_ok=True,mode=0o700)
sdk=args.android_jar
badging=subprocess.run(['nice','-n','19',str(args.aapt),'dump','badging',str(args.apk)],check=True,capture_output=True,text=True).stdout
fields=dict(re.findall(r"([A-Za-z][A-Za-z0-9]*)='([^']*)'",badging.splitlines()[0]))
if fields.get('name')!='mc.snakenest.launcher.android.debug':raise ValueError('Fixtures target the debug application only')
base=int(fields['versionCode'])
if not 0<base<2_100_000_000:raise ValueError('Invalid fixture base version')
def run(args): subprocess.run(['nice','-n','19']+list(map(str,args)),check=True,capture_output=True)
key=out/'fixture-only.jks'
if not key.exists():run(['keytool','-genkeypair','-keystore',key,'-storepass','fixture-password','-keypass','fixture-password','-alias','fixture','-dname','CN=Updater verifier fixture only','-keyalg','RSA','-validity','1'])
for name,package,code,wrong in [('future','mc.snakenest.launcher.android.debug',base+1,False),('same-version','mc.snakenest.launcher.android.debug',base,False),('wrong-package','mc.snakenest.launcher.android.unrelated',base+1,False),('wrong-signer','mc.snakenest.launcher.android.debug',base+1,True)]:
 directory=out/name;directory.mkdir(exist_ok=True);manifest=directory/'AndroidManifest.xml';apk=out/(name+'.apk')
 manifest.write_text(f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{package}" android:versionCode="{code}" android:versionName="0.2.1"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="34"/><application android:label="Updater verifier fixture"/></manifest>')
 run([args.aapt,'package','-f','-M',manifest,'-I',sdk,'-F',apk])
 run([args.apksigner,'sign','--ks',key if wrong else r/'app_pojavlauncher/debug.keystore','--ks-pass','pass:fixture-password' if wrong else 'pass:android','--ks-key-alias','fixture' if wrong else 'androiddebugkey','--key-pass','pass:fixture-password' if wrong else 'pass:android','--v4-signing-enabled','false',apk])
print('4 APK factices signés créés ; aucun n’a été installé.')
