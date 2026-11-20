# splice2.py <js> <skel> <out>
# @@E:n@@  = biểu thức ba ngôi `isEn() ? A : B` (nguyên văn) bắt đầu từ dòng n (hoặc dòng chứa chuỗi ~text); kết thúc ở ; , hoặc ) cân ngoặc.
# @@R:a-b@@ = nguyên văn các dòng a..b (khi chúng đã là Java hợp lệ).  `en ?` và `english ?` cũng được nhận như isEn() ?
# @@S:n@@  = chuỗi "..." đầu tiên từ dòng n.   @@T:n@@ = gọi t(...) như splice.py
import re,sys
js=open(sys.argv[1],encoding='utf-8').read(); L=js.split('\n'); skel=open(sys.argv[2],encoding='utf-8').read()
def off(a):
    if a.startswith('~'):
        i=js.index(a[1:]); return js.rfind('\n',0,i)+1
    return sum(len(l)+1 for l in L[:int(a)-1])
def scan(j,stop):
    d=0;q=None
    while True:
        c=js[j]
        if q:
            if c=='\\': j+=1
            elif c==q: q=None
        elif c in '"\'': q=c
        elif c in '([{': d+=1
        elif c in ')]}':
            if d==0: return j
            d-=1
        elif d==0 and c in stop: return j
        j+=1
def E(a):
    i=re.compile(r'(?<![\w.])(isEn\(\)|english|en) \?').search(js,off(a)).start(); return js[i:scan(i,';,')].rstrip()
def S(a):
    i=js.index('"',off(a)); j=i+1
    while js[j]!='"':
        j+=2 if js[j]=='\\' else 1
    return js[i:j+1]
def R(a):
    x,y=map(int,a.split('-')); return '\n'.join(L[x-1:y])
def T(a):
    m=re.compile(r'(?<![A-Za-z0-9_.$])t\(').search(js,off(a)); i=m.start(); j=scan(m.end(),'')+1; return js[i:j]
out=re.sub(r'@@([ESTR]):([^@]+)@@',lambda m:{'E':E,'S':S,'T':T,'R':R}[m.group(1)](m.group(2)),skel)
open(sys.argv[3],'w',encoding='utf-8').write(out)
