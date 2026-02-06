#!/usr/bin/env python3
import argparse
import json
import os
import sys
import datetime

def get_file_size(filepath):
    if not os.path.exists(filepath):
        print(f"Error: File {filepath} not found.")
        return None
    return os.path.getsize(filepath)

def load_json(filepath):
    if not os.path.exists(filepath):
        return {}
    try:
        with open(filepath, 'r') as f:
            return json.load(f)
    except json.JSONDecodeError:
        return {}

def save_json(filepath, data):
    with open(filepath, 'w') as f:
        json.dump(data, f, indent=2)

def main():
    parser = argparse.ArgumentParser(description='Measure file size and track metrics.')
    parser.add_argument('--file', required=True, help='Path to the file to measure')
    parser.add_argument('--flavor', required=True, help='Build flavor (e.g., default, lite)')
    parser.add_argument('--build-type', required=True, help='Build type (e.g., debug, release)')
    parser.add_argument('--config', required=True, help='Path to size limits config JSON')
    parser.add_argument('--output-json', required=True, help='Path to output report JSON')
    parser.add_argument('--output-md', required=True, help='Path to output report Markdown')
    parser.add_argument('--metrics-db', help='Path to metrics history JSON (optional)')
    parser.add_argument('--label', default='APK', help='Label for the file (e.g. APK, AAB)')

    args = parser.parse_args()

    # Measure size
    size_bytes = get_file_size(args.file)
    if size_bytes is None:
        sys.exit(1)

    size_mb = size_bytes / (1024 * 1024)

    # Load limits
    config = load_json(args.config)
    # Config key format: "{flavor}{BuildType}" e.g., "defaultDebug" or "liteRelease"
    # Or maybe more structured. Let's try to match "flavor" and "build_type".
    # Assuming config structure: { "defaultDebug": { "max_size_bytes": 123 } }

    config_key = f"{args.flavor}{args.build_type.capitalize()}"
    if args.label == 'AAB':
        config_key += "Bundle"

    limit_info = config.get(config_key, {})
    max_size = limit_info.get('max_size_bytes')

    status = "OK"
    diff_percent = 0

    if max_size and size_bytes > max_size:
        status = "WARNING"
        diff_percent = ((size_bytes - max_size) / max_size) * 100

    # History
    history = []
    prev_size_mb = None

    if args.metrics_db:
        history = load_json(args.metrics_db)
        if not isinstance(history, list):
            history = []

        # Find previous entry for comparison
        # Filter by same flavor, build_type, label
        # We assume history is loaded from a file that might contain other entries
        # Sort by date
        relevant = [x for x in history if x.get('flavor') == args.flavor and x.get('build_type') == args.build_type and x.get('label') == args.label]
        relevant.sort(key=lambda x: x.get('date', ''))

        if relevant:
            last = relevant[-1]
            prev_size_mb = last.get('size_mb')

        # Add current entry
        entry = {
            "date": datetime.datetime.now().isoformat(),
            "flavor": args.flavor,
            "build_type": args.build_type,
            "label": args.label,
            "size_bytes": size_bytes,
            "size_mb": round(size_mb, 2)
        }
        history.append(entry)
        # Only save if we are strictly updating db (which is what this script does if metrics-db is passed)
        # But for PR builds, we might pass a read-only DB.
        # The script currently always writes back.
        # I'll keep it as is, but in build.yml I might need to handle the fact that I don't want to save back to the read-only source,
        # or I just pass a copy.
        save_json(args.metrics_db, history)

    # Generate Report JSON
    report = {
        "file": args.file,
        "flavor": args.flavor,
        "build_type": args.build_type,
        "label": args.label,
        "size_bytes": size_bytes,
        "size_mb": size_mb,
        "limit_bytes": max_size,
        "status": status,
        "key": config_key
    }

    save_json(args.output_json, report)

    # Generate Report Markdown
    md_content = f"### {args.label} Size: {args.flavor} {args.build_type}\n\n"
    md_content += f"**Size:** {size_mb:.2f} MB ({size_bytes} bytes)\n"

    if max_size:
        max_mb = max_size / (1024 * 1024)
        icon = "✅" if status == "OK" else "⚠️"
        md_content += f"**Limit:** {max_mb:.2f} MB\n"
        md_content += f"**Status:** {icon} {status}\n"
        if status != "OK":
             md_content += f"**Exceeds by:** {diff_percent:.1f}%\n"
    else:
        md_content += "**Limit:** Not defined\n"

    if prev_size_mb is not None:
        diff_mb = size_mb - prev_size_mb
        diff_icon = "➖"
        if diff_mb > 0:
            diff_icon = "📈" # Increasing
        elif diff_mb < 0:
            diff_icon = "📉" # Decreasing

        md_content += f"**Previous:** {prev_size_mb:.2f} MB\n"
        md_content += f"**Change:** {diff_icon} {diff_mb:+.2f} MB\n"

    with open(args.output_md, 'w') as f:
        f.write(md_content)

    print(f"Measured {args.file}: {size_mb:.2f} MB. Status: {status}")

if __name__ == "__main__":
    main()
