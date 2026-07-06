import asyncio
import importlib
import sys
import types
import unittest
from pathlib import Path
from unittest.mock import patch


class _FastApiStub:
    def __init__(self, *args, **kwargs):
        pass

    def on_event(self, _event):
        return lambda func: func

    def get(self, _path):
        return lambda func: func

    def post(self, _path, response_model=None):
        return lambda func: func


class _HttpExceptionStub(Exception):
    def __init__(self, status_code, detail):
        super().__init__(detail)
        self.status_code = status_code
        self.detail = detail


class _BaseModelStub:
    def __init__(self, **kwargs):
        for key, value in kwargs.items():
            setattr(self, key, value)


def _install_import_stubs():
    fastapi_module = types.ModuleType("fastapi")
    fastapi_module.FastAPI = _FastApiStub
    fastapi_module.HTTPException = _HttpExceptionStub
    sys.modules["fastapi"] = fastapi_module

    pydantic_module = types.ModuleType("pydantic")
    pydantic_module.BaseModel = _BaseModelStub
    sys.modules["pydantic"] = pydantic_module

    prometheus_module = types.ModuleType("prometheus_client")
    prometheus_module.CONTENT_TYPE_LATEST = "text/plain"
    prometheus_module.generate_latest = lambda: b""
    sys.modules["prometheus_client"] = prometheus_module

    classifier_module = types.ModuleType("classifier")
    classifier_module.classify_batch = lambda _images: []
    classifier_module.load_model = lambda: None
    sys.modules["classifier"] = classifier_module


class MainStartupTest(unittest.TestCase):
    def setUp(self):
        _install_import_stubs()
        sys.path.insert(0, str(Path(__file__).resolve().parent))
        sys.modules.pop("main", None)

    def tearDown(self):
        if sys.path and sys.path[0] == str(Path(__file__).resolve().parent):
            sys.path.pop(0)
        sys.modules.pop("main", None)

    def test_startup_starts_kafka_consumer_when_mode_is_kafka(self):
        module = importlib.import_module("main")

        class ThreadStub:
            def __init__(self, target, daemon):
                self.target = target
                self.daemon = daemon
                self.started = False

            def start(self):
                self.started = True

        created_threads = []

        def create_thread(*args, **kwargs):
            thread = ThreadStub(*args, **kwargs)
            created_threads.append(thread)
            return thread

        with patch.dict(module.os.environ, {"MODE": "kafka"}, clear=False), patch.object(
            module.threading, "Thread", side_effect=create_thread
        ), patch.object(module, "load_model") as load_model:
            asyncio.run(module.startup())

        load_model.assert_called_once()
        self.assertEqual(1, len(created_threads))
        self.assertTrue(created_threads[0].daemon)
        self.assertTrue(created_threads[0].started)


if __name__ == "__main__":
    unittest.main()
