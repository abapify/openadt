Generate a RAP service on destination {{destination}} for {{scenario}}.

1. abap_generators-list_generators — read each 'description'; pick the generator that matches the scenario (e.g. 'x-ui-service' for a full RAP + UI service).
2. abap_generators-get_schema — give a package name first ({{package}}). If the schema lists referenceObjectTypes, ask me for those too.
3. abap_generators-generate_objects — generate; this resolves transport via abap_transport-get automatically.
   Show me the generated objects and the service binding name.
