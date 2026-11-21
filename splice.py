# splice.py: thay @@T:n@@ bằng biểu thức t(...) nguyên văn (cân bằng ngoặc, đọc chuỗi an toàn) bắt đầu từ dòng n của file JS.
import re,sys,glob
js=open(glob.glob('/tmp/a/*/scripts/lazash.js')[0],encoding='utf-8').read().split('\n')
src=open(sys.argv[1],encoding='utf-8').read()
def grab(n):
    off=sum(len(l)+1 for l in js[:n-1]); txt='\n'.join(js)
    m=re.compile(r'(?<![A-Za-z0-9_.$])t\(').search(txt,off)
    i=m.start(); j=m.end(); d=1; q=None
    while d:
        c=txt[j]
        if q:
            if c=='\\': j+=1
            elif c==q: q=None
        elif c in '"\'': q=c
        elif c=='(': d+=1
        elif c==')': d-=1
        j+=1
    return txt[i:j]
out=re.sub(r'@@T:(\d+)@@',lambda m:grab(int(m.group(1))),src)
open(sys.argv[2],'w',encoding='utf-8').write(out)
