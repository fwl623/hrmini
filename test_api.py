import json, urllib.request

# Login
login_data = json.dumps({"username":"13800001001","password":"Admin@12345"}).encode()
req = urllib.request.Request('http://localhost:8080/api/v1/auth/login',
    data=login_data,
    headers={'Content-Type':'application/json'})
resp = urllib.request.urlopen(req)
token = json.loads(resp.read())['data']['accessToken']
print(f"Got token: {token[:20]}...")

# Call monthly punch stats
req2 = urllib.request.Request('http://localhost:8080/api/v1/profile/attendance/punch/monthly',
    headers={'Authorization':f'Bearer {token}'})
resp2 = urllib.request.urlopen(req2)
result = json.loads(resp2.read())
print(json.dumps(result, indent=2, ensure_ascii=False))
