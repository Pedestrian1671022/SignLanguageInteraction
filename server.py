#!/usr/bin/env python
# -*- coding: UTF-8 -*-

import os
import socket
import time
import threading

flag = False


class Receive(threading.Thread):
    def __init__(self, name, soc):
        threading.Thread.__init__(self)
        self.name = name
        self.soc = soc

    def run(self):
        global flag
        data = self.soc.recv(1024)
        if not data:
            flag = True


class Send(threading.Thread):
    def __init__(self, name, soc):
        threading.Thread.__init__(self)
        self.name = name
        self.soc = soc

    def run(self):
        global flag
        while True:
            if flag:
                flag = False
                break
            self.soc.sendall("刘鑫,你好啊!\n")
            time.sleep(5)


HOST = out = os.popen("ifconfig | grep 'inet addr:' | grep -v '127.0.0.1' | cut -d: -f2 | awk '{print $1}' | head -1").read()
PORT = 9527
soc = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
soc.bind((HOST, PORT))
soc.listen(1)
while True:
    conn, address = soc.accept()
    print 'Connected by', address
    thread1 = Receive("receive", conn)
    thread2 = Send("send", conn)
    thread2.start()
    thread1.start()
    thread1.join()
    thread2.join()
    conn.close()
