#!/usr/bin/env python3
"""Exercise the real publishing configuration against a temporary file repository."""

import argparse
import copy
from html import escape
from pathlib import Path
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile


ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
ET.register_namespace("", NS["m"])
GROUP = "local.zrlog.buildcheck"
VERSION = "1.0.0-SNAPSHOT"


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(value, encoding="utf-8")


def fixture(directory):
    # Keep the actual parent, build plugins and snapshot profile: this also tests
    # the Central extension inherited from zrlog-base, not a hand-written substitute.
    original = ET.parse(ROOT / "pom.xml").getroot()
    project = ET.Element(f"{{{NS['m']}}}project")
    for name in ("modelVersion", "parent", "properties", "repositories", "build", "profiles"):
        project.append(copy.deepcopy(original.find(f"m:{name}", NS)))
    for name, value in (("groupId", GROUP), ("artifactId", "snapshot-check"),
                        ("version", VERSION), ("packaging", "pom")):
        ET.SubElement(project, f"{{{NS['m']}}}{name}").text = value
    modules = ET.SubElement(project, f"{{{NS['m']}}}modules")
    for module in ("common", "consumer"):
        ET.SubElement(modules, f"{{{NS['m']}}}module").text = module
    ET.ElementTree(project).write(directory / "pom.xml", encoding="utf-8", xml_declaration=True)

    common_build = ET.parse(ROOT / "zrlog-admin-common/pom.xml").getroot().find("m:build", NS)
    for module in ("common", "consumer"):
        body = "" if module == "common" else f"""
            <dependencies>
                <dependency><groupId>{GROUP}</groupId><artifactId>common</artifactId>
                    <version>{VERSION}</version></dependency>
                <dependency><groupId>{GROUP}</groupId><artifactId>common</artifactId>
                    <version>{VERSION}</version><type>test-jar</type>
                    <classifier>tests</classifier><scope>test</scope></dependency>
            </dependencies>"""
        write(directory / module / "pom.xml", f"""<project xmlns="{NS['m']}">
            <modelVersion>4.0.0</modelVersion>
            <parent><groupId>{GROUP}</groupId><artifactId>snapshot-check</artifactId>
                <version>{VERSION}</version></parent>
            <artifactId>{module}</artifactId>{body}
        </project>""")
        if module == "common":
            child = ET.parse(directory / module / "pom.xml")
            child.getroot().append(copy.deepcopy(common_build))
            child.write(directory / module / "pom.xml", encoding="utf-8", xml_declaration=True)
    write(directory / "common/src/main/java/fixture/Value.java",
          'package fixture; public class Value { public static final String TEXT = "snapshot"; }')
    write(directory / "common/src/test/java/com/zrlog/admin/support/Support.java",
          'package com.zrlog.admin.support; public class Support { public static final int NUMBER = 7; }')
    write(directory / "consumer/src/main/java/fixture/Consumer.java",
          'package fixture; public class Consumer { public String value() { return Value.TEXT; } }')
    write(directory / "consumer/src/test/java/fixture/ConsumerTest.java", """
        package fixture;
        import org.junit.Test;
        import static org.junit.Assert.assertEquals;
        import com.zrlog.admin.support.Support;
        public class ConsumerTest {
            @Test public void usesSharedFixture() { assertEquals(7, Support.NUMBER); }
        }
    """)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--maven-repo", type=Path, help="Optional Maven dependency cache")
    parser.add_argument("--offline", action="store_true", help="Resolve dependencies only from the local cache")
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="zrlog-snapshot-deploy-") as temporary:
        directory = Path(temporary)
        settings = directory / "settings.xml"
        # Maven Deploy rejects -o even for file:// destinations. A file mirror
        # prevents dependency network access while allowing the local deployment.
        mirror = ""
        if args.offline:
            cache = (args.maven_repo or Path.home() / ".m2/repository").resolve()
            mirror = f"""<mirrors><mirror><id>local-cache</id><mirrorOf>*</mirrorOf>
                <url>{escape(cache.as_uri())}</url></mirror></mirrors>"""
        write(settings, f'<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">{mirror}</settings>')
        fixture(directory)
        command = [str(ROOT / "mvnw"), "-B", "-s", str(settings)]
        if args.maven_repo:
            command.append(f"-Dmaven.repo.local={args.maven_repo.resolve()}")

        def run(*arguments, success=True):
            result = subprocess.run(command + list(arguments), cwd=directory, text=True,
                                    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=180)
            if (result.returncode == 0) != success:
                raise AssertionError(result.stdout)
            return result.stdout

        repository = directory / "published"
        log = run("-Psnapshot", "clean", "deploy",
                  f"-DaltSnapshotDeploymentRepository=snapshot-check::{repository.as_uri()}")
        base = repository / GROUP.replace(".", "/")
        for artifact, attachments in {
            "snapshot-check": {(None, "pom")},
            "common": {(None, "pom"), (None, "jar"), ("sources", "jar"), ("tests", "jar")},
            "consumer": {(None, "pom"), (None, "jar"), ("sources", "jar")},
        }.items():
            version_dir = base / artifact / VERSION
            metadata = ET.parse(version_dir / "maven-metadata.xml").getroot()
            versions = metadata.findall("versioning/snapshotVersions/snapshotVersion")
            assert {(v.findtext("classifier"), v.findtext("extension")) for v in versions} == attachments
            assert len({v.findtext("value") for v in versions}) == 1, "Attachments must use one snapshot version"
            for version in versions:
                classifier = version.findtext("classifier")
                suffix = f"-{classifier}" if classifier else ""
                path = version_dir / f"{artifact}-{version.findtext('value')}{suffix}.{version.findtext('extension')}"
                assert path.is_file(), path
                assert path.with_name(path.name + ".sha1").is_file(), path
            metadata_uploads = [line for line in log.splitlines() if "Uploaded to snapshot-check:" in line
                                and f"/{artifact}/{VERSION}/maven-metadata.xml" in line]
            assert len(metadata_uploads) == 1, metadata_uploads
        tests_jar = next((base / "common" / VERSION).glob("*-tests.jar"))
        with zipfile.ZipFile(tests_jar) as jar:
            assert "com/zrlog/admin/support/Support.class" in jar.namelist()
        assert not list(repository.rglob("*.asc"))
        assert not list(repository.rglob("*-javadoc.jar"))
        assert len(re.findall(r"--- surefire:[^\n]* @ consumer ---", log)) == 1
        assert "Tests run: 1, Failures: 0, Errors: 0" in log
        last_test = log.rfind("Tests run:")
        assert log.index("Uploading to snapshot-check:") > last_test, "Upload must wait for all tests"
        print("PASS: grouped snapshot upload, metadata, source/test attachments, and full test execution")

        # A failure in the final module must not publish the earlier successful module.
        test_file = directory / "consumer/src/test/java/fixture/ConsumerTest.java"
        write(test_file, test_file.read_text().replace("assertEquals(7, Support.NUMBER)", "assertEquals(8, Support.NUMBER)"))
        failed_repository = directory / "failed-publish"
        log = run("-Psnapshot", "clean", "deploy",
                  f"-DaltSnapshotDeploymentRepository=snapshot-check::{failed_repository.as_uri()}", success=False)
        assert "Failures: 1" in log
        assert "Uploading to snapshot-check:" not in log
        assert not list(failed_repository.rglob("*.pom"))
        print("PASS: late test failure leaves the target repository empty")

        # Without the snapshot profile, Maven must retain the original release path.
        effective = directory / "release-effective.xml"
        run("-N", "org.apache.maven.plugins:maven-help-plugin:3.5.2:effective-pom", f"-Doutput={effective}")
        model = ET.parse(effective).getroot()
        for key in ("gpg.skip", "maven.javadoc.skip"):
            assert model.findtext(f"m:properties/m:{key}", namespaces=NS) != "true"
        plugins = {p.findtext("m:artifactId", namespaces=NS): p
                   for p in model.findall("m:build/m:plugins/m:plugin", NS)}
        publisher = plugins["central-publishing-maven-plugin"]
        assert publisher.findtext("m:extensions", namespaces=NS) == "true"
        assert publisher.findtext("m:configuration/m:waitUntil", namespaces=NS) == "published"
        assert any(e.findtext("m:phase", namespaces=NS) == "deploy"
                   for e in publisher.findall("m:executions/m:execution", NS))
        for name in ("maven-gpg-plugin", "maven-javadoc-plugin"):
            assert any(e.findtext("m:phase", namespaces=NS) == "deploy"
                       for e in plugins[name].findall("m:executions/m:execution", NS))
        print("PASS: release publishing, signatures and Javadoc remain enabled")


if __name__ == "__main__":
    main()
