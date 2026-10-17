from pathlib import Path
import difflib,hashlib,json,shutil,subprocess
root=Path('/mnt/ALPH/code/viptv-org/app');p=Path('/tmp/viptv-debian-final313-2026-10-15');artifact=Path((p/'artifact-directory').read_text().strip());e=root/'docs/evidence/linux-final313-debian-2026-10-15';e.mkdir(exist_ok=True)
sha=lambda f:hashlib.sha256(f.read_bytes()).hexdigest()
for f in p.iterdir():
 if f.is_file():shutil.copy2(f,e/f.name)
result=p/'result'
for f in result.iterdir():
 if f.is_file():shutil.copy2(f,e/f.name)
for f in ['result','home']:
 source=result/'smoke'/f
 if source.is_file():shutil.copy2(source,e/('smoke-'+f+'.txt'))
vault=result/'vault';v=e/'vault';v.mkdir(exist_ok=True)
for f in vault.iterdir():
 if f.is_file():shutil.copy2(f,v/f.name)
for f in (vault/'installed-final313').iterdir():
 if f.suffix in ['.json','.png','.log']:shutil.copy2(f,v/f.name)
shutil.copy2('/tmp/viptv-desktop-vault-2026-10-15/DesktopVaultHarness.java',v/'DesktopVaultHarness.java')
for name in ['receipt.json','control.txt','postinst.txt','desktop-entry.txt','extracted-runtime.json','extracted-abi.json','jpackage.log']:
 shutil.copy2(artifact/name,e/('packaged-'+name))
shutil.copy2('/tmp/viptv-addon-account-final-gate.log',e/'source-final-gate.log')
original=Path('/tmp/viptv-debian-install-2026-10-15/jdk21-template.postinst').read_text();override=(artifact/'jpackage-resources/postinst').read_text();(e/'verified-jdk21-template.postinst').write_text(original);(e/'postinst-resource-delta.patch').write_text(''.join(difflib.unified_diff(original.splitlines(True),override.splitlines(True),fromfile='verified-JDK21-template.postinst',tofile='postinst-resource')))
proof=json.loads((v/'proof.json').read_text());passed=json.loads((p/'passed.json').read_text());inspect=json.loads((p/'inspect.json').read_text())[0];cleanup=json.loads((p/'cleanup.json').read_text());receipt=json.loads((artifact/'receipt.json').read_text());manifest=json.loads((p/'image-manifest.json').read_text());native= json.loads((artifact/'extracted-runtime.json').read_text())['native_resource']['sha256'];jars={name:digest for name,digest in manifest.items() if name.endswith('.jar') and 'composeApp-desktop-' in name}
assert int((e/'apt-install-deb.exit').read_text())==0 and not (e/'dpkg-audit.txt').read_text().strip()
assert (e/'menu-directory-before.txt').read_text().strip()=='absent'
assert sha(artifact/receipt['package'])==passed['sha256']
for phase in ['write-local','read-local','legacy-remote','credential-matching','locked-access','cancel-startup','read']:assert proof[phase]['exit_code']==0
expected=['canceled_restore_job_completed:true','new_local_choice_busy:false','new_local_choice_factory_calls:1','new_local_choice_screen:Profiles','new_local_choice_notice:true'];assert all(line in proof['cancel-startup']['stdout'] for line in expected)
assert proof['prompter_owned_after_locked_operations']=='(false,)'
assert all('/opt/viptv-experiment/lib/runtime/lib/server/libjvm.so' in proof[phase]['loaded_jvm'] for phase in ['actual-main-unlocked-local-restore','actual-main-locked-storage-warning'])
pids=[inspect['State']['Pid'],inspect['State']['ConmonPid']]+[x['pid'] for x in cleanup if x['action']=='terminate-exact-own-pause'];states={str(pid):Path('/proc/'+str(pid)).exists() for pid in pids};assert not any(states.values())
unit=subprocess.run(['systemctl','--user','is-active','viptv-debian-final313-20261015'],text=True,capture_output=True).stdout.strip();assert unit=='inactive'
summary={'passed':True,'artifact':str((artifact/receipt['package']).relative_to(root)),'sha256':passed['sha256'],'bytes':(artifact/receipt['package']).stat().st_size,'debian_revision':2,'application_jars':jars,'native_sha256':native,'source_snapshot':str((artifact/'source-snapshot').relative_to(root)),'source_manifest_files':len(json.loads((p/'source-manifest.json').read_text())),'image_manifest_files':len(manifest),'source_gate':'source-final-gate.log; BUILD SUCCESSFUL3m31s, root reported313desktop +isolatedWasm/normalAndroid/Linuximage','base_image':inspect['Config']['Image'],'base_debian':'13.7 trixie-slim x86_64','rootless':True,'privileged':inspect['HostConfig']['Privileged'],'artifact_read_only':not inspect['Mounts'][0]['RW'],'menu_directory_absent_before_install':True,'apt_update_exit_code':int((e/'apt-update.exit').read_text()),'apt_install_exit_code':0,'dpkg_status':(e/'dpkg-status.txt').read_text().strip(),'dpkg_audit_empty':True,'installed_file_verification_clean':True,'native_smoke_before_gui_dependencies':True,'bundled_jvm':'21.0.12.1; mapped from /opt/viptv-experiment/lib/runtime/lib/server/libjvm.so','private_vault_checks_passed':True,'original_main_screenshots_visually_reviewed':True,'skiko_renderer':'SOFTWARE under owned Xvfb','cleanup':{'container_id':inspect['Id'],'own_store_containers':[],'user_unit':unit,'owned_pid_exists':states,'cached_own_base_image_retained':True},'host_packages_changed':False,'user_wallet_display_audio_accessed':False,'host_manual_services_or_default_containers_touched':False,'prior_artifact_relationship':'Original ebc3 revision1 failure and c4ec revision2 installer-only old287 snapshot remain separately preserved. This final313 d869 artifact is freshly qualified.','scope':'Fresh Debian13.7 x86_64 normal package-manager install, menu registration, installed native/JVM/SQLite, private vault and original Main. No new playback claim; historical97e VTT component continuity is separate. PhysicalGPU/display/audio, Windows/macOS and distribution signing not qualified.'}
(e/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');shutil.copy2(e/'summary.json',artifact/'debian-install-qualification.json')
provenance={'debian_install_evidence':str(e.relative_to(root)),'source_gate_sha256':sha(e/'source-final-gate.log'),'image_manifest_sha256':sha(p/'image-manifest.json'),'source_manifest_sha256':sha(p/'source-manifest.json'),'isolated_dpkg':json.loads((root/'build/packaging-tools/dpkg-1.23.7-1/provenance.json').read_text()),'jdk_archive_sha256':'ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94','packaging_script_sha256':{name:sha(root/'tools'/name) for name in ['package_linux.py','audit_linux_runtime.py','verify_desktop.py']},'signed_distribution':False,'scope':summary['scope']};(artifact/'provenance.json').write_text(json.dumps(provenance,indent=2)+'\n');shutil.copy2(artifact/'provenance.json',e/'provenance.json')
print(json.dumps({'evidence':str(e),'sha256':summary['sha256'],'application_jars':jars,'cleanup_complete':not any(states.values())},indent=2))
