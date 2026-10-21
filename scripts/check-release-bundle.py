#!/usr/bin/env python3
"""Build the real release reactor in isolation and inspect its unuploaded bundle."""

import argparse
import io
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile


ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}



def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--maven-repo", type=Path, help="Optional Maven dependency cache")
    args = parser.parse_args()

    model = ET.parse(ROOT / "pom.xml").getroot()
    publisher = next(plugin for plugin in model.findall("m:build/m:plugins/m:plugin", NS)
                     if plugin.findtext("m:artifactId", namespaces=NS) == "central-publishing-maven-plugin")
    # In 0.8.0 skipPublishing runs staging and bundling, then returns before upload.
    # Later versions changed its behavior; fail closed instead of guessing a flag.
    if publisher.findtext("m:version", namespaces=NS) != "0.8.0":
        raise AssertionError("Bundle check requires verified Central Publishing 0.8.0 behavior")
    version = model.findtext("m:version", namespaces=NS).removesuffix("-SNAPSHOT") + "-bundle-check"

    artifacts = {model.findtext("m:artifactId", namespaces=NS): "pom"}
    for module in model.findall("m:modules/m:module", NS):
        child = ET.parse(ROOT / module.text / "pom.xml").getroot()
        artifacts[child.findtext("m:artifactId", namespaces=NS)] = "jar"

    with tempfile.TemporaryDirectory(prefix="zrlog-release-bundle-") as temporary:
        directory = Path(temporary)
        project = directory / "project"
        project.mkdir()
        tracked = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT).decode().split("\0")
        for filename in filter(None, tracked):
            source = ROOT / filename
            if source.is_file():
                destination = project / filename
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(source, destination)

        # No publishing credentials are read from the user's settings.
        settings = directory / "settings.xml"
        # The legacy client dereferences server settings even when upload is disabled.
        # Inert fixture values satisfy initialization; no real credentials are loaded.
        settings.write_text('<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">'
                            '<servers><server><id>central</id><username>bundle-check</username>'
                            '<password>unpublished-fixture</password></server></servers></settings>', encoding="utf-8")
        command = [str(project / "mvnw"), "-B", "-ntp", "-s", str(settings), "-gs", str(settings)]
        repository = args.maven_repo.resolve() if args.maven_repo else directory / "repository"
        command.append(f"-Dmaven.repo.local={repository}")

        subprocess.run(command + ["org.codehaus.mojo:versions-maven-plugin:2.21.0:set",
                                 f"-DnewVersion={version}", "-DprocessAllModules=true",
                                 "-DgenerateBackupPoms=false", "-DupdateBuildOutputTimestamp=false"], cwd=project, check=True)
        # Exercise deploy's Javadoc, staging and bundling without signing or uploading.
        # Tests have already run in the preceding CI step.
        subprocess.run(command + ["-PnodeBuild", "clean", "deploy", "-DskipTests", "-Dgpg.skip=true",
                                 "-DskipPublishing=true", "-DignorePublishedComponents=false",
                                 "-DcentralBaseUrl=http://127.0.0.1:9"],
                       cwd=project, check=True)

        bundle = project / "target/central-publishing/central-bundle.zip"
        with zipfile.ZipFile(bundle) as archive:
            files = {name for name in archive.namelist() if not name.endswith("/")}
            components = set()
            for name in files:
                path = PurePosixPath(name)
                if len(path.parts) != 5 or path.parts[:2] != ("com", "hibegin"):
                    raise AssertionError(f"Unexpected bundle path: {name}")
                artifact, version, filename = path.parts[2:]
                if artifact not in artifacts or version != version:
                    raise AssertionError(f"Unexpected component: {name}")
                if not filename.startswith(f"{artifact}-{version}.") and not filename.startswith(f"{artifact}-{version}-"):
                    raise AssertionError(f"Local repository metadata leaked into bundle: {name}")
                components.add(artifact)
            if components != set(artifacts):
                raise AssertionError(f"Missing components: {set(artifacts) - components}")
            for artifact, packaging in artifacts.items():
                prefix = f"com/hibegin/{artifact}/{version}/{artifact}-{version}"
                required = [f"{prefix}.pom"]
                if packaging == "jar":
                    required.extend(f"{prefix}{suffix}.jar" for suffix in ("", "-sources", "-javadoc"))
                for name in required:
                    for suffix in ("", ".md5", ".sha1"):
                        if name + suffix not in files:
                            raise AssertionError(f"Missing release attachment: {name + suffix}")
                if packaging == "jar":
                    with zipfile.ZipFile(io.BytesIO(archive.read(f"{prefix}-javadoc.jar"))) as javadoc:
                        if "index.html" not in javadoc.namelist():
                            raise AssertionError(f"Empty Javadoc attachment: {artifact}")
        print(f"PASS: {len(artifacts)} components contain POMs, module JARs, sources, Javadocs and checksums")
        print("PASS: no local repository metadata; verified skipPublishing kept the bundle off Central; signatures skipped")


if __name__ == "__main__":
    main()
