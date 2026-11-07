"""Fake Android TV adbd for testing the IBRA HOME installer end to end."""
import socket, struct, os, sys, base64, hashlib, threading
A=dict(CNXN=0x4e584e43,AUTH=0x48545541,OPEN=0x4e45504f,OKAY=0x59414b4f,CLSE=0x45534c43,WRTE=0x45545257)
N={v:k for k,v in A.items()}
APK=sys.argv[2]; PORT=int(sys.argv[1]); STUBBORN=len(sys.argv)>3
trusted=[]; state=dict(home="com.google.android.tvlauncher/com.google.android.tvlauncher.MainActivity",
    installed=False, files={}, disabled=set(), broadcasts=[], prompts=0, log=[])
def rd(c,n):
    b=b''
    while len(b)<n:
        x=c.recv(n-len(b))
        if not x: raise EOFError
        b+=x
    return b
def recv(c):
    h=rd(c,24); cmd,a0,a1,ln,crc,mg=struct.unpack('<6I',h)
    assert mg==cmd^0xffffffff,'bad magic'
    d=rd(c,ln) if ln else b''
    assert sum(d)&0xffffffff==crc,'bad checksum'
    return cmd,a0,a1,d
def send(c,cmd,a0,a1,d=b''):
    c.sendall(struct.pack('<6I',cmd,a0,a1,len(d),sum(d)&0xffffffff,cmd^0xffffffff)+d)
def parse_key(blob):
    b64=blob.rstrip(b'\0').split(b' ')[0]; raw=base64.b64decode(b64)
    assert len(raw)==524, len(raw)
    words,n0inv=struct.unpack('<II',raw[:8]); assert words==64
    n=int.from_bytes(raw[8:264],'little'); rr=int.from_bytes(raw[264:520],'little'); e=struct.unpack('<i',raw[520:])[0]
    assert (n0inv*n)%2**32 == 2**32-1, 'n0inv wrong'
    assert rr==pow(2,4096,n), 'rr wrong'
    assert e==65537
    return n,e
def verify(key,token,sig):
    n,e=key; m=pow(int.from_bytes(sig,'big'),e,n).to_bytes(256,'big')
    prefix=bytes.fromhex('3021300906052b0e03021a05000414')
    return m == b'\x00\x01'+b'\xff'*(256-3-15-20)+b'\x00'+prefix+token
def shell(cmd):
    s=state; s['log'].append(cmd); ours='tv.ibra.tuner.launcher'
    if cmd=='getprop ro.product.model': return 'Fake TV 4K'
    if cmd=='getprop ro.build.version.release': return '11'
    if cmd=='getprop ro.build.version.sdk': return '30'
    if cmd=='getprop ro.serialno': return 'ABC123'
    if cmd.startswith('pm list packages'): return ('package:'+ours) if s['installed'] and ours in cmd else ''
    if 'resolve-activity' in cmd:
        h=s['home']
        if h and h.split('/')[0] in s['disabled']: h=None
        return 'priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true\n'+(h or 'android/com.android.internal.app.ResolverActivity')
    if 'query-activities' in cmd:
        l=[] if 'com.google.android.tvlauncher' in s['disabled'] else ['com.google.android.tvlauncher/com.google.android.tvlauncher.MainActivity']
        l+=['com.google.android.tungsten.setupwraith/.SetupWraithActivity']
        if s['installed']: l.append(ours+'/.HomeActivity')
        return '\n'.join(l)
    if cmd.startswith('pm install'):
        p=cmd.split()[-1]
        if s['files'].get(p)!=open(APK,'rb').read(): return 'Failure [INSTALL_FAILED_INVALID_APK]'
        s['installed']=True; return 'Success'
    if cmd.startswith('rm -f'): s['files'].pop(cmd.split()[-1],None); return ''
    if cmd.startswith('am broadcast'): s['broadcasts'].append(cmd); return 'Broadcasting: Intent\nBroadcast completed: result=1'
    if 'set-home-activity' in cmd:
        comp=cmd.split()[-1]
        if comp.startswith(ours) and not s['installed']: return 'Error'
        if STUBBORN and comp.startswith(ours) and 'com.google.android.tvlauncher' not in s['disabled']: return 'Success: default home activity set'  # ignored!
        s['home']=comp; return 'Success: default home activity set to: '+comp
    if 'cmd role' in cmd: return ''
    if cmd.startswith('input keyevent'): return ''
    if cmd.startswith('pm disable-user'): p=cmd.split()[-1]; s['disabled'].add(p); return f'Package {p} new state: disabled-user'
    if cmd.startswith('pm enable'): p=cmd.split()[-1]; s['disabled'].discard(p); return f'Package {p} new state: enabled'
    if cmd.startswith('pm uninstall'):
        s['installed']=False
        if s['home'] and s['home'].startswith(ours): s['home']=None
        return 'Success'
    return 'unknown: '+cmd
def stream_write(c,lid,rid,data,maxd=4096):
    for i in range(0,len(data),maxd):
        send(c,A['WRTE'],lid,rid,data[i:i+maxd])
        while True:
            cmd,a0,a1,d=recv(c)
            if cmd==A['OKAY']: break
def handle(c):
    nextid=[100]
    cmd,a0,a1,d=recv(c); assert cmd==A['CNXN'] and d.startswith(b'host::'), (hex(cmd),d)
    token=os.urandom(20); send(c,A['AUTH'],1,0,token)
    while True:
        cmd,a0,a1,d=recv(c); assert cmd==A['AUTH']
        if a0==2:
            if any(verify(k,token,d) for k in trusted): break
            token=os.urandom(20); send(c,A['AUTH'],1,0,token)
        elif a0==3:
            k=parse_key(d); state['prompts']+=1; trusted.append(k); print('PROMPT: user pressed Allow for',d.rstrip(b'\0').split(b' ',1)[1]); break
    send(c,A['CNXN'],0x01000001,256*1024,b'device::ro.product.name=fake;features=cmd\0')
    while True:
        try: cmd,a0,a1,d=recv(c)
        except EOFError: return
        if cmd!=A['OPEN']: continue  # stray CLSE/OKAY ignored like adbd
        lid=nextid[0]; nextid[0]+=1; rid=a0; svc=d.rstrip(b'\0').decode()
        send(c,A['OKAY'],lid,rid)
        if svc.startswith('shell:'):
            out=shell(svc[6:]).encode()
            if out: stream_write(c,lid,rid,out)
            send(c,A['CLSE'],lid,rid)
            pass  # real adbd needs no CLSE reply
        elif svc=='sync:':
            buf=b''; path=None; data=b''; done=False
            while True:
                cmd,x0,x1,d=recv(c)
                if cmd==A['CLSE']: break
                assert cmd==A['WRTE']; send(c,A['OKAY'],lid,rid); buf+=d
                while len(buf)>=8:
                    idt=buf[:4].decode(); ln=struct.unpack('<I',buf[4:8])[0]
                    if idt=='SEND':
                        if len(buf)<8+ln: break
                        path,mode=buf[8:8+ln].decode().rsplit(',',1); assert mode==str(0o100644),mode; buf=buf[8+ln:]
                    elif idt=='DATA':
                        if len(buf)<8+ln: break
                        assert ln<=65536; data+=buf[8:8+ln]; buf=buf[8+ln:]
                    elif idt=='DONE':
                        buf=buf[8:]; state['files'][path]=data; stream_write(c,lid,rid,b'OKAY'+struct.pack('<I',0))
                    elif idt=='QUIT':
                        buf=buf[8:]; send(c,A['CLSE'],lid,rid)
                    else: raise Exception('bad sync id '+idt)
srv=socket.socket(); srv.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1); srv.bind(('127.0.0.1',PORT)); srv.listen(5)
print('fake adbd listening',PORT,'stubborn' if STUBBORN else '',flush=True)
for i in range(int(os.environ.get('CONNS','3'))):
    c,_=srv.accept()
    try: handle(c)
    except Exception as e: import traceback; traceback.print_exc()
    finally: c.close()
    print('STATE after conn',i+1,{k:v for k,v in state.items() if k not in('files','log')},flush=True)
