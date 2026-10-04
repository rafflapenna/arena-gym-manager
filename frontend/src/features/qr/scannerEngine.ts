import { prepareZXingModule } from '@yudiel/react-qr-scanner';
import wasmUrl from 'zxing-wasm/reader/zxing_reader.wasm?url';

// barcode-detector (used by @yudiel/react-qr-scanner) would otherwise fetch the zxing
// WASM binary from the jsdelivr CDN at runtime — a silent hard dependency on an external
// host, unacceptable for a gym network. Bundle it and point the loader at our own asset.
//
// The override MUST go through the prepareZXingModule re-exported by the scanner package:
// barcode-detector wraps its own zxing-wasm instance, so calling zxing-wasm/reader's
// prepareZXingModule directly configures a different instance and the CDN fetch survives.
// The direct zxing-wasm dependency exists only to bundle the .wasm asset — npm dedupes it
// to the exact version barcode-detector pins, keeping engine and binary in lockstep.
prepareZXingModule({
  overrides: {
    locateFile: (path: string, prefix: string) => (path.endsWith('.wasm') ? wasmUrl : prefix + path),
  },
});

export { Scanner } from '@yudiel/react-qr-scanner';
