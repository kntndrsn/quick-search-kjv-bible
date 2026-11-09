import { readPayload } from './data.js';
import { searchData } from './search-engine.js';

let dataPromise;
self.onmessage = async ({ data: request }) => {
  try {
    if (!dataPromise) {
      dataPromise = readPayload(request.manifest.search, request.manifest.version).catch((error) => {
        dataPromise = null;
        throw error;
      });
    }
    const data = await dataPromise;
    self.postMessage({ id: request.id, result: searchData(data, request.query) });
  } catch (error) {
    self.postMessage({ id: request.id, error: error.message });
  }
};
