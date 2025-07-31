import chromadb

client = chromadb.HttpClient(host='localhost', port=8000)

# List available collections
collections = client.list_collections()
print("Collections:", collections)