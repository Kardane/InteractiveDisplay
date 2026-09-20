#!/usr/bin/env bash
set -euo pipefail

show_usage() {
  cat <<'USAGE'
사용법:
  ./new-fabric-server-mod.sh -ProjectName <name> -PackageBase <pkg> -OutputDir <path> [옵션]
  ./new-fabric-server-mod.sh --project-name <name> --package-base <pkg> --output-dir <path> [옵션]

옵션:
  --mod-id <id>                    mod id 직접 지정 (미지정 시 ProjectName 기반 자동 생성)
  --minecraft-version <ver>        기본값: 1.21.8
  --yarn-mappings <ver>            기본값: 1.21.8+build.1
  --loom-version <ver>             기본값: 1.8-SNAPSHOT
  --loader-version <ver>           기본값: TODO_LOADER_VERSION
  --fabric-api-version <ver>       기본값: TODO_FABRIC_API_VERSION
  --polymer-version <ver>          기본값: TODO_POLYMER_VERSION

예시:
  ./new-fabric-server-mod.sh \
    -ProjectName sample-mod \
    -PackageBase com.example.sample \
    -OutputDir /tmp/work
USAGE
}

project_name=""
package_base=""
output_dir=""
mod_id=""
minecraft_version="1.21.8"
yarn_mappings="1.21.8+build.1"
loom_version="1.8-SNAPSHOT"
loader_version="TODO_LOADER_VERSION"
fabric_api_version="TODO_FABRIC_API_VERSION"
polymer_version="TODO_POLYMER_VERSION"

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help|-Help|--Help)
      show_usage
      exit 0
      ;;
    -ProjectName|--project-name)
      project_name="${2:-}"
      shift 2
      ;;
    -PackageBase|--package-base)
      package_base="${2:-}"
      shift 2
      ;;
    -OutputDir|--output-dir)
      output_dir="${2:-}"
      shift 2
      ;;
    --mod-id)
      mod_id="${2:-}"
      shift 2
      ;;
    --minecraft-version)
      minecraft_version="${2:-}"
      shift 2
      ;;
    --yarn-mappings)
      yarn_mappings="${2:-}"
      shift 2
      ;;
    --loom-version)
      loom_version="${2:-}"
      shift 2
      ;;
    --loader-version)
      loader_version="${2:-}"
      shift 2
      ;;
    --fabric-api-version)
      fabric_api_version="${2:-}"
      shift 2
      ;;
    --polymer-version)
      polymer_version="${2:-}"
      shift 2
      ;;
    *)
      echo "[ERROR] 알 수 없는 인자: $1" >&2
      show_usage
      exit 1
      ;;
  esac
done

if [[ -z "$project_name" || -z "$package_base" || -z "$output_dir" ]]; then
  echo "[ERROR] 필수 인자가 부족합니다." >&2
  show_usage
  exit 1
fi

if ! [[ "$project_name" =~ ^[a-zA-Z0-9._-]+$ ]]; then
  echo "[ERROR] ProjectName 형식이 잘못됐습니다. 허용: 영문/숫자/점/언더스코어/하이픈" >&2
  exit 1
fi

if ! [[ "$package_base" =~ ^[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)+$ ]]; then
  echo "[ERROR] PackageBase 형식이 잘못됐습니다. 예: com.example.mod" >&2
  exit 1
fi

if [[ -z "$mod_id" ]]; then
  mod_id="$(echo "$project_name" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9_-]+/-/g; s/-+/-/g; s/^-+//; s/-+$//')"
fi

if ! [[ "$mod_id" =~ ^[a-z][a-z0-9_-]{1,63}$ ]]; then
  echo "[ERROR] mod id 형식이 잘못됐습니다. 예: sample_mod, sample-mod" >&2
  exit 1
fi

camel_name="$(echo "$project_name" | sed -E 's/[^a-zA-Z0-9]+/ /g' | awk '{for(i=1;i<=NF;i++){ $i=toupper(substr($i,1,1)) tolower(substr($i,2)) } printf "%s", $0}' | tr -d ' ')"
if [[ "$camel_name" == *Mod ]]; then
  main_class_name="$camel_name"
else
  main_class_name="${camel_name}Mod"
fi

project_dir="$output_dir/$project_name"
if [[ -e "$project_dir" ]]; then
  echo "[ERROR] 프로젝트 디렉터리가 이미 존재합니다: $project_dir" >&2
  exit 1
fi

package_path="${package_base//./\/}"
java_root="$project_dir/src/main/java/$package_path"
resources_dir="$project_dir/src/main/resources"

mkdir -p \
  "$java_root/core" \
  "$java_root/mixin" \
  "$java_root/polymer" \
  "$resources_dir"

cat > "$project_dir/settings.gradle" <<EOF_SET
rootProject.name = '$project_name'
EOF_SET

cat > "$project_dir/gradle.properties" <<EOF_PROP
org.gradle.jvmargs=-Xmx2G
minecraft_version=$minecraft_version
yarn_mappings=$yarn_mappings
loader_version=$loader_version
fabric_api_version=$fabric_api_version
polymer_version=$polymer_version
maven_group=$package_base
archives_base_name=$project_name
EOF_PROP

cat > "$project_dir/build.gradle" <<EOF_BUILD
plugins {
    id 'fabric-loom' version '$loom_version'
    id 'maven-publish'
}

version = '0.1.0'
group = project.maven_group

base {
    archivesName = project.archives_base_name
}

repositories {
    mavenCentral()
    maven { url = 'https://maven.fabricmc.net/' }
    maven { url = 'https://maven.nucleoid.xyz/' }
}

dependencies {
    minecraft "com.mojang:minecraft:\${project.minecraft_version}"
    mappings "net.fabricmc:yarn:\${project.yarn_mappings}:v2"
    modImplementation "net.fabricmc:fabric-loader:\${project.loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:\${project.fabric_api_version}"
    modImplementation "eu.pb4:polymer-core:\${project.polymer_version}"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

processResources {
    inputs.property 'version', project.version

    filesMatching('fabric.mod.json') {
        expand 'version': project.version
    }
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
}
EOF_BUILD

cat > "$resources_dir/fabric.mod.json" <<EOF_MOD
{
  "schemaVersion": 1,
  "id": "$mod_id",
  "version": "\${version}",
  "name": "$project_name",
  "description": "Fabric 서버사이드 모드",
  "authors": ["your-name"],
  "license": "MIT",
  "environment": "server",
  "entrypoints": {
    "main": [
      "$package_base.$main_class_name"
    ]
  },
  "mixins": [
    "$mod_id.mixins.json"
  ],
  "depends": {
    "fabricloader": ">=0.16.0",
    "minecraft": "~$minecraft_version",
    "fabric-api": "*"
  }
}
EOF_MOD

cat > "$resources_dir/$mod_id.mixins.json" <<EOF_MIXINS
{
  "required": true,
  "package": "$package_base.mixin",
  "compatibilityLevel": "JAVA_21",
  "mixins": [
    "ServerLifecycleMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
EOF_MIXINS

cat > "$java_root/$main_class_name.java" <<EOF_MAIN
package $package_base;

import $package_base.core.ModServices;
import $package_base.polymer.PolymerBridge;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class $main_class_name implements ModInitializer {
    public static final String MOD_ID = "$mod_id";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // 서버 초기화 진입점: core -> polymer 순으로 연결
        LOGGER.info("[{}] initialized", MOD_ID);
        new ModServices().bootstrap();
        new PolymerBridge().register();
    }
}
EOF_MAIN

cat > "$java_root/core/ModServices.java" <<EOF_CORE
package $package_base.core;

public class ModServices {
    public void bootstrap() {
        // 핵심 서버 로직 초기화 지점
    }
}
EOF_CORE

cat > "$java_root/polymer/PolymerBridge.java" <<EOF_POLY
package $package_base.polymer;

public class PolymerBridge {
    public void register() {
        // Polymer 연동은 이 계층에서만 처리
    }
}
EOF_POLY

cat > "$java_root/mixin/ServerLifecycleMixin.java" <<EOF_MIXIN
package $package_base.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class ServerLifecycleMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void onServerTickHead(CallbackInfo ci) {
        // 최소 주입 예시: 필요 기능만 추가
    }
}
EOF_MIXIN

cat > "$project_dir/.gitignore" <<'EOF_GITIGNORE'
.gradle/
build/
run/
out/
*.iml
*.ipr
*.iws
EOF_GITIGNORE

cat > "$project_dir/README.md" <<EOF_README
# $project_name

## 기준
- Minecraft: $minecraft_version
- Java: 21
- Fabric Loader: $loader_version
- Fabric API: $fabric_api_version
- Polymer: $polymer_version

## 시작 순서
1. gradle.properties의 TODO 버전을 실제 호환 버전으로 교체
2. ./gradlew clean build
3. 개발 서버 부팅 후 핵심 시나리오 검증
EOF_README

echo "[OK] 생성 완료: $project_dir"
echo "[INFO] mod id: $mod_id"
echo "[INFO] 다음 단계: verify-mod-env.sh -ProjectDir $project_dir"
if [[ "$loader_version" == TODO_* || "$fabric_api_version" == TODO_* || "$polymer_version" == TODO_* ]]; then
  echo "[WARN] loader/fabric/polymer 버전이 TODO 상태입니다. 배포 전 반드시 교체하세요."
fi
