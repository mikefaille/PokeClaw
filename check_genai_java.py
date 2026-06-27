import urllib.request
import json

repos = ["google/google-genai-java", "googleapis/google-genai-java", "google/gemini-api-java"]

for repo in repos:
    url = f"https://api.github.com/repos/{repo}/commits"
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        response = urllib.request.urlopen(req)
        data = json.loads(response.read().decode('utf-8'))
        print(f"Found repo {repo}")
        for commit in data[:5]:
            print(commit['commit']['message'].split('\n')[0])
        break
    except Exception as e:
        print(f"Error for {repo}: {e}")
