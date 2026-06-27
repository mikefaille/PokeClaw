import urllib.request
import json
import os

url = "https://ai.google.dev/api/gemini/computer-use"
try:
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    response = urllib.request.urlopen(req)
    html = response.read().decode('utf-8')
    if "computer_use" in html.lower() or "1000" in html.lower():
        print("Found computer use references.")
except Exception as e:
    print(e)
