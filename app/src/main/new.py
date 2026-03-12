from google.oauth2 import service_account
from google.auth.transport.requests import Request

SCOPES = ["https://www.googleapis.com/auth/spreadsheets"]

credentials = service_account.Credentials.from_service_account_file(
    "key.json",
    scopes=SCOPES,
)

credentials.refresh(Request())

print("ACCESS TOKEN:")
print(credentials.token)