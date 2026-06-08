Fetch OData service information on destination {{destination}} for {{serviceBindingName}}.

1. abap_business_services-fetch_services — get services for the binding. For OData V4: if isPublished is false, STOP and tell me to publish the service binding first.
2. Present the available services and versions and WAIT for me to pick one.
3. abap_business_services-fetch_service_information — only after I selected a specific service version; return the service URL, entity sets and navigations.
