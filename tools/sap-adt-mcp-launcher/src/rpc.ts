/** Single import surface for vscode-jsonrpc (avoids duplicate ParameterStructures). */
export {
  createMessageConnection,
  createClientPipeTransport,
  generateRandomPipeName,
  ParameterStructures,
  Trace,
  TraceFormat,
  type MessageConnection,
} from "vscode-jsonrpc/node.js";
