from __future__ import annotations

import argparse
import contextlib
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

TOOLS_DIR = Path(__file__).resolve().parents[1]
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

import resource_compiler


class ResourceCompilerPathSafetyTest(unittest.TestCase):
    def make_source(self, path: Path) -> Path:
        (path / "BinOutput").mkdir(parents=True)
        (path / "ExcelBinOutput").mkdir()
        sentinel = path / "source.sentinel"
        sentinel.write_text("source", encoding="utf-8")
        return sentinel

    def make_manifest(self, output: Path, source: Path) -> None:
        output.mkdir(parents=True, exist_ok=True)
        (output / ".resource-compiler.json").write_text(
            json.dumps(
                {
                    "compiler_version": resource_compiler.COMPILER_VERSION,
                    "game_version": "7.1.0",
                    "generated_at": "2026-10-05T00:00:00+00:00",
                    "source": str(source.resolve()),
                    "overlay": None,
                    "normalizers": {"ability_duplicate_keys": []},
                }
            ),
            encoding="utf-8",
        )

    def args(
        self,
        source: Path,
        output: Path,
        overlay: Path | None = None,
        *,
        force: bool = True,
    ) -> argparse.Namespace:
        return argparse.Namespace(
            source=source,
            output=output,
            overlay=overlay,
            game_version="7.1.0",
            force=force,
        )

    def assert_rejected_before_delete(
        self,
        args: argparse.Namespace,
        sentinels: list[Path],
        message_fragment: str,
    ) -> None:
        caught: SystemExit | None = None
        with (
            mock.patch.object(resource_compiler.shutil, "rmtree") as rmtree,
            mock.patch.object(resource_compiler, "copy_source"),
            mock.patch.object(resource_compiler, "overlay_tree"),
            mock.patch.object(resource_compiler, "normalize_ability_json", return_value=[]),
            mock.patch.object(resource_compiler, "write_json"),
            mock.patch.object(resource_compiler, "doctor", return_value=0),
            contextlib.redirect_stdout(io.StringIO()),
            contextlib.redirect_stderr(io.StringIO()),
        ):
            try:
                resource_compiler.build(args)
            except SystemExit as exc:
                caught = exc

        self.assertFalse(rmtree.called, "unsafe path reached shutil.rmtree")
        self.assertIsNotNone(caught, "unsafe path was not rejected")
        self.assertIn(message_fragment, str(caught).lower())
        for sentinel in sentinels:
            self.assertTrue(sentinel.exists(), f"sentinel disappeared: {sentinel}")

    def test_rejects_source_equal_output_before_delete(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / "source"
            sentinel = self.make_source(source)
            self.assert_rejected_before_delete(
                self.args(source, source), [sentinel], "source"
            )

    def test_rejects_output_parent_of_source_before_delete(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / "output"
            source = output / "source"
            sentinel = self.make_source(source)
            self.make_manifest(output, source)
            output_sentinel = output / "output.sentinel"
            output_sentinel.write_text("output", encoding="utf-8")
            self.assert_rejected_before_delete(
                self.args(source, output), [sentinel, output_sentinel], "overlap"
            )

    def test_rejects_output_child_of_source_before_delete(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / "source"
            sentinel = self.make_source(source)
            output = source / "generated"
            self.make_manifest(output, source)
            output_sentinel = output / "output.sentinel"
            output_sentinel.write_text("output", encoding="utf-8")
            self.assert_rejected_before_delete(
                self.args(source, output), [sentinel, output_sentinel], "overlap"
            )

    def test_rejects_overlay_equal_output_before_delete(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "source"
            source_sentinel = self.make_source(source)
            output = root / "output"
            self.make_manifest(output, source)
            output_sentinel = output / "output.sentinel"
            output_sentinel.write_text("output", encoding="utf-8")
            self.assert_rejected_before_delete(
                self.args(source, output, output),
                [source_sentinel, output_sentinel],
                "overlap",
            )

    def test_rejects_overlay_inside_output_before_delete(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "source"
            source_sentinel = self.make_source(source)
            output = root / "output"
            self.make_manifest(output, source)
            overlay = output / "overlay"
            overlay.mkdir()
            overlay_sentinel = overlay / "overlay.sentinel"
            overlay_sentinel.write_text("overlay", encoding="utf-8")
            self.assert_rejected_before_delete(
                self.args(source, output, overlay),
                [source_sentinel, overlay_sentinel],
                "overlap",
            )

    def test_rejects_output_inside_overlay_before_delete(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "source"
            source_sentinel = self.make_source(source)
            overlay = root / "overlay"
            overlay.mkdir()
            overlay_sentinel = overlay / "overlay.sentinel"
            overlay_sentinel.write_text("overlay", encoding="utf-8")
            output = overlay / "output"
            self.make_manifest(output, source)
            output_sentinel = output / "output.sentinel"
            output_sentinel.write_text("output", encoding="utf-8")
            self.assert_rejected_before_delete(
                self.args(source, output, overlay),
                [source_sentinel, overlay_sentinel, output_sentinel],
                "overlap",
            )

    def test_rejects_repository_root_before_delete(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "source"
            source_sentinel = self.make_source(source)
            repository_root = root / "repo"
            self.make_manifest(repository_root, source)
            repo_sentinel = repository_root / "repo.sentinel"
            repo_sentinel.write_text("repo", encoding="utf-8")
            with mock.patch.object(
                resource_compiler, "REPOSITORY_ROOT", repository_root.resolve(), create=True
            ):
                self.assert_rejected_before_delete(
                    self.args(source, repository_root),
                    [source_sentinel, repo_sentinel],
                    "repository root",
                )

    def test_rejects_filesystem_root_before_delete(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / "source"
            source_sentinel = self.make_source(source)
            filesystem_root = Path(temp).resolve()
            while filesystem_root.parent != filesystem_root:
                filesystem_root = filesystem_root.parent
            self.assert_rejected_before_delete(
                self.args(source, filesystem_root),
                [source_sentinel],
                "filesystem root",
            )

    def test_rejects_unmarked_existing_output_before_delete(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "source"
            source_sentinel = self.make_source(source)
            output = root / "output"
            output.mkdir()
            output_sentinel = output / "output.sentinel"
            output_sentinel.write_text("output", encoding="utf-8")
            self.assert_rejected_before_delete(
                self.args(source, output),
                [source_sentinel, output_sentinel],
                "generated resource directory",
            )

    def test_rejects_file_overlay_before_delete(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "source"
            source_sentinel = self.make_source(source)
            output = root / "output"
            self.make_manifest(output, source)
            output_sentinel = output / "output.sentinel"
            output_sentinel.write_text("output", encoding="utf-8")
            overlay = root / "overlay.json"
            overlay.write_text("not a directory", encoding="utf-8")
            overlay_sentinel = root / "overlay.sentinel"
            overlay_sentinel.write_text("overlay", encoding="utf-8")
            self.assert_rejected_before_delete(
                self.args(source, output, overlay),
                [source_sentinel, output_sentinel, overlay_sentinel],
                "overlay path is not a directory",
            )

    def test_build_allows_sibling_source_and_output(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "source"
            source_sentinel = self.make_source(source)
            output = root / "output"
            with (
                mock.patch.object(resource_compiler, "doctor", return_value=0),
                contextlib.redirect_stdout(io.StringIO()),
                contextlib.redirect_stderr(io.StringIO()),
            ):
                result = resource_compiler.build(self.args(source, output, force=False))
            self.assertEqual(0, result)
            self.assertTrue((output / source_sentinel.name).is_file())
            self.assertTrue((output / ".resource-compiler.json").is_file())

    def test_force_replaces_only_generated_sibling_output(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "source"
            self.make_source(source)
            output = root / "output"
            with (
                mock.patch.object(resource_compiler, "doctor", return_value=0),
                contextlib.redirect_stdout(io.StringIO()),
                contextlib.redirect_stderr(io.StringIO()),
            ):
                resource_compiler.build(self.args(source, output, force=False))
                stale = output / "stale.sentinel"
                stale.write_text("stale", encoding="utf-8")
                fresh = source / "fresh.sentinel"
                fresh.write_text("fresh", encoding="utf-8")
                result = resource_compiler.build(self.args(source, output, force=True))
            self.assertEqual(0, result)
            self.assertFalse(stale.exists())
            self.assertTrue((output / fresh.name).is_file())
            manifest = json.loads(
                (output / ".resource-compiler.json").read_text(encoding="utf-8")
            )
            self.assertEqual(resource_compiler.COMPILER_VERSION, manifest["compiler_version"])


if __name__ == "__main__":
    unittest.main()
