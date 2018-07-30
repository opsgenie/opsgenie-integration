config.load_incluster_config()

v1 = client.CoreV1Api()
w = watch.Watch()
for event in w.stream(v1.list_pod_for_all_namespaces, _request_timeout=60 * 60):
	requests.post(OPSGENIE_URL, data=event, headers={"application/json"})