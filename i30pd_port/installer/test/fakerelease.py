"""A stand-in for the GitHub releases API for the installers' self-update (run.sh): GET /latest and /tags/test answer
release JSON for the ZIPs in DIR/stable and DIR/test (name, size, digest "sha256:...", browser_download_url), and
/files/<channel>/<name> serves them. A file <name>.bad next to a ZIP makes its listed digest wrong (a tampered download).
usage: fakerelease.py DIR   (listens on a free port and writes it to DIR/port)"""
import hashlib, http.server, json, os, sys

root = sys.argv[1]


class H(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a): pass

    def send(self, code, body=b''):
        self.send_response(code); self.send_header('Content-Length', str(len(body))); self.end_headers(); self.wfile.write(body)

    def do_GET(self):
        ch = {'/latest': 'stable', '/tags/test': 'test'}.get(self.path)
        if ch:
            d, assets = os.path.join(root, ch), []
            for n in sorted(os.listdir(d)) if os.path.isdir(d) else []:
                if n.endswith('.zip'):
                    b = open(os.path.join(d, n), 'rb').read()
                    dig = '0' * 64 if os.path.exists(os.path.join(d, n + '.bad')) else hashlib.sha256(b).hexdigest()
                    assets.append({'name': n, 'size': len(b), 'digest': 'sha256:' + dig,
                                   'browser_download_url': 'http://127.0.0.1:%d/files/%s/%s' % (self.server.server_port, ch, n)})
            return self.send(200, json.dumps({'tag_name': ch, 'assets': assets}).encode())
        p = self.path.split('/')
        if len(p) == 4 and p[1] == 'files' and os.path.isfile(os.path.join(root, p[2], p[3])):
            return self.send(200, open(os.path.join(root, p[2], p[3]), 'rb').read())
        self.send(404)


s = http.server.HTTPServer(('127.0.0.1', 0), H)
open(os.path.join(root, 'port'), 'w').write(str(s.server_port))
s.serve_forever()
