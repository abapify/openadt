Create an ABAP object on destination {{destination}}.

Follow this chain, one step at a time, and stop to ask me whenever a value is missing:

1. abap_list_destinations — confirm the destination is active.
2. abap_creation-get_all_creatable_objects — list creatable types (target type: {{objectType}}).
3. abap_creation-get_object_type_details — find out what input the type needs.
4. abap_creation-run_validation — validate {{name}} in {{package}}; if it returns errors, fix the input and validate again.
5. abap_transport-get — resolve the transport request (NEVER call abap_transport-create directly). If no transport is available (new objects / dev-only destinations), stop and ask me: I may have a transport number to give you, or I may want to skip the transport entirely (local object, no transport layer).
6. abap_creation-create_object — create it.
7. abap_activate_objects — activate the new object.
   Report what was created and its transport request.
