"""Acquire public model weights for packaging; run on the build host, never the phone."""
import hashlib
import json
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "app/src/main/assets/models"
DEST.mkdir(parents=True, exist_ok=True)

def fetch(url, maximum=24 * 1024 * 1024):
    request = urllib.request.Request(url, headers={"User-Agent": "ODS-build"})
    with urllib.request.urlopen(request, timeout=90) as response:
        data = response.read(maximum + 1)
    if len(data) > maximum:
        raise ValueError("Model exceeds size budget")
    return data

provenance = []
def save(name, url, license_name):
    data = fetch(url)
    if name.endswith('.tflite') and data[4:8] != b'TFL3':
        raise ValueError("Expected TFLite weights, received invalid content")
    if name.endswith('.onnx') and (len(data) < 10000 or data.lstrip().startswith(b'<')):
        raise ValueError("Expected ONNX weights, received invalid content")
    (DEST / name).write_bytes(data)
    provenance.append(dict(file=name, source=url, license=license_name,
                           bytes=len(data), sha256=hashlib.sha256(data).hexdigest()))

# Resolve the repository revision once so model and license use the same snapshot.
revision = json.loads(fetch('https://api.github.com/repos/raspberrypi/AI_enhance/commits/main'))['sha']
base = f'https://raw.githubusercontent.com/raspberrypi/AI_enhance/{revision}'
save('dcenet.tflite', base + '/dcenet.tflite', 'BSD-2-Clause')
save('ZERO_DCE_LICENSE.txt', base + '/LICENSE', 'BSD-2-Clause')
# Public weight ID from DocAligner's published lcnet050 inference configuration.
save('docaligner.onnx', 'https://drive.usercontent.google.com/download?id=1J7cRuupeEIudYrH_CCSV9WvFfu9JM_qU&export=download&confirm=t', 'Apache-2.0')
save('DOCALIGNER_LICENSE.txt', 'https://raw.githubusercontent.com/DocsaidLab/DocAligner/main/LICENSE', 'Apache-2.0')
(DEST / 'provenance.json').write_text(json.dumps(provenance, indent=2), encoding='utf-8')

if __name__ == '__main__':
    import numpy as np
    import onnxruntime as ort
    from ai_edge_litert.interpreter import Interpreter
    session = ort.InferenceSession(str(DEST / 'docaligner.onnx'), providers=['CPUExecutionProvider'])
    assert session.get_inputs()[0].name == 'img'
    outputs = dict(zip([o.name for o in session.get_outputs()], session.run(None, {'img': np.zeros((1,3,256,256), np.float32)})))
    assert outputs['points'].size == 8 and outputs['has_obj'].size == 1
    interpreter = Interpreter(model_path=str(DEST / 'dcenet.tflite'))
    interpreter.resize_tensor_input(0, [1,128,128,4]); interpreter.allocate_tensors()
    assert interpreter.get_output_details()[0]['shape'].tolist() == [1,128,128,24]
    interpreter.set_tensor(interpreter.get_input_details()[0]['index'], np.zeros((1,128,128,4), np.float32))
    interpreter.invoke()
    assert np.isfinite(interpreter.get_tensor(interpreter.get_output_details()[0]['index'])).all()
    print(json.dumps(provenance, indent=2))
