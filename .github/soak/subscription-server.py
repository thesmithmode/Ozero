#!/usr/bin/env python3
import argparse
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse


class SubscriptionHandler(SimpleHTTPRequestHandler):
    request_count = 0

    def do_GET(self):
        if urlparse(self.path).path == "/subscription.txt":
            type(self).request_count += 1
            self.path = (
                "/subscription-v1.txt"
                if type(self).request_count == 1
                else "/subscription-v2.txt"
            )
        return super().do_GET()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--directory", required=True)
    parser.add_argument("--port", required=True, type=int)
    args = parser.parse_args()
    directory = Path(args.directory)
    handler = partial(SubscriptionHandler, directory=directory)
    ThreadingHTTPServer(("0.0.0.0", args.port), handler).serve_forever()


if __name__ == "__main__":
    main()
