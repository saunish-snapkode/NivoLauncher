import re,sys,subprocess,xml.etree.ElementTree as ET
adb=['/home/saunish/Android/Sdk/platform-tools/adb','-s','emulator-5554']
def dump():
    subprocess.run(adb+['shell','uiautomator','dump','/sdcard/ui.xml'],capture_output=True)
    return subprocess.run(adb+['exec-out','cat','/sdcard/ui.xml'],capture_output=True,text=True).stdout
density=2.625  # emulator 420dpi => 2.625
def audit(label):
    xml=dump()
    root=ET.fromstring(xml)
    small=[];unlabeled=[];n=0
    for e in root.iter('node'):
        if e.get('package')!='com.snapkode.nivolauncher': continue
        if e.get('clickable')!='true' and e.get('long-clickable')!='true': continue
        n+=1
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',e.get('bounds'))
        l,t,r,b=map(int,m.groups())
        w=(r-l)/density;h=(b-t)/density
        text=e.get('text') or ''; desc=e.get('content-desc') or ''
        # a clickable parent usually has labelled children; check descendants
        has=bool(text or desc) or any((c.get('text') or c.get('content-desc')) for c in e.iter('node'))
        if min(w,h)<47.5: small.append((round(w),round(h),text or desc or e.get('class')))
        if not has: unlabeled.append((round(w),round(h),e.get('class'),e.get('bounds')))
    print(f'== {label}: {n} clickable; {len(small)} under 48dp; {len(unlabeled)} unlabeled')
    for s in small[:12]: print('   small',s)
    for u in unlabeled[:12]: print('   unlabeled',u)
if __name__=='__main__': audit(sys.argv[1] if len(sys.argv)>1 else 'screen')
