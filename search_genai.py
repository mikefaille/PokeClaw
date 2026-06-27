import urllib.request
import json

url = "https://api.github.com/repos/google/gemini-api-java/issues?q=computer+use"
try:
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    response = urllib.request.urlopen(req)
    data = json.loads(response.read().decode('utf-8'))
    print(f"Found {len(data)} issues related to computer use.")
    for issue in data:
        print(f"- {issue['title']}")
except Exception as e:
    print(e)

url2 = "https://api.github.com/search/code?q=repo:google/google-genai-java+computer_use"
try:
    req2 = urllib.request.Request(url2, headers={'User-Agent': 'Mozilla/5.0'})
    response2 = urllib.request.urlopen(req2)
    data2 = json.loads(response2.read().decode('utf-8'))
    print(f"Found {len(data2.get('items', []))} code references in google-genai-java.")
except Exception as e:
    print(f"Code search error: {e}")
