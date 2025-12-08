#!/usr/bin/env python3
"""
Ice Python Test Client - HTTP server for testing Ice Python SDK.
"""

import argparse
import logging
import signal
import sys
import os

import yaml

# Add SDK to path for development
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '../../../sdks/python/src'))

import ice
from flask import Flask, request, jsonify

# Import leaf nodes to trigger registration
import flow  # noqa: F401
import result  # noqa: F401
import none  # noqa: F401

# Configure logging
logging.basicConfig(
    level=logging.DEBUG,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

app = Flask(__name__)


def load_config(config_path: str = None) -> dict:
    """Load configuration from YAML file."""
    if config_path is None:
        # Default config file path
        config_path = os.path.join(os.path.dirname(__file__), 'config.yml')
    
    if not os.path.exists(config_path):
        logger.warning(f"Config file not found: {config_path}, using defaults")
        return {}
    
    with open(config_path, 'r') as f:
        config = yaml.safe_load(f) or {}
    
    logger.info(f"Loaded config from {config_path}")
    return config

# Global client
client = None


@app.route('/test', methods=['POST'])
def handle_test():
    """Handle POST /test with a JSON body."""
    data = request.get_json() or {}
    
    pack = ice.Pack()
    
    # Parse pack fields
    if 'iceId' in data:
        pack.ice_id = int(data['iceId'])
    if 'scene' in data:
        pack.scene = str(data['scene'])
    if 'confId' in data:
        pack.conf_id = int(data['confId'])
    if 'debug' in data:
        pack.debug = int(data['debug'])
    if 'roam' in data and isinstance(data['roam'], dict):
        for k, v in data['roam'].items():
            pack.roam.put(k, v)
    
    # Process
    ctx_list = ice.sync_process(pack)
    
    # Return result
    result_list = []
    for ctx in ctx_list:
        item = {'iceId': ctx.ice_id}
        if ctx.pack is not None:
            item['roam'] = ctx.pack.roam.to_dict()
        item['processInfo'] = ctx.get_process_info()
        result_list.append(item)
    
    return jsonify(result_list)


@app.route('/recharge', methods=['GET'])
def handle_recharge():
    """Handle GET /recharge?cost=xxx&uid=xxx."""
    cost = request.args.get('cost', 0, type=int)
    uid = request.args.get('uid', 0, type=int)
    
    pack = ice.Pack(scene='recharge')
    pack.roam.put('cost', cost)
    pack.roam.put('uid', uid)
    
    ice.sync_process(pack)
    
    return jsonify(pack.to_dict())


@app.route('/consume', methods=['GET'])
def handle_consume():
    """Handle GET /consume?cost=xxx&uid=xxx."""
    cost = request.args.get('cost', 0, type=int)
    uid = request.args.get('uid', 0, type=int)
    
    pack = ice.Pack(scene='consume')
    pack.roam.put('cost', cost)
    pack.roam.put('uid', uid)
    
    ice.sync_process(pack)
    
    return jsonify(pack.to_dict())


@app.route('/health', methods=['GET'])
def handle_health():
    """Handle GET /health."""
    return 'OK', 200


def main():
    global client
    
    parser = argparse.ArgumentParser(description='Ice Python Test Client')
    parser.add_argument('--config', '-c', type=str, default=None, help='Config file path (default: config.yml)')
    parser.add_argument('--port', type=int, default=None, help='HTTP server port (overrides config)')
    parser.add_argument('--storage', type=str, default=None, help='Ice data storage path (overrides config)')
    parser.add_argument('--app', type=int, default=None, help='Application ID (overrides config)')
    args = parser.parse_args()
    
    # Load config from file
    config = load_config(args.config)
    
    # Get values (command line args override config file)
    port = args.port or config.get('server', {}).get('port', 8085)
    ice_config = config.get('ice', {})
    app_id = args.app or ice_config.get('app', 1)
    storage_path = args.storage or ice_config.get('storage', {}).get('path', './ice-data')
    poll_interval = ice_config.get('poll-interval', 5)
    heartbeat_interval = ice_config.get('heartbeat-interval', 10)
    parallelism = ice_config.get('pool', {}).get('parallelism', -1)
    
    # Create and start file client
    logger.info(f"Starting ice client: app={app_id}, storage={storage_path}")
    
    try:
        client = ice.FileClient(
            app=app_id,
            storage_path=storage_path,
            parallelism=parallelism,
            poll_interval=float(poll_interval),
            heartbeat_interval=float(heartbeat_interval),
        )
        client.start()
        client.wait_started()
        logger.info(f"Ice client started, loaded version: {client.loaded_version}")
    except Exception as e:
        logger.error(f"Failed to start ice client: {e}")
        sys.exit(1)
    
    # Setup graceful shutdown
    def shutdown_handler(signum, frame):
        logger.info("Shutting down...")
        if client:
            client.destroy()
        sys.exit(0)
    
    signal.signal(signal.SIGINT, shutdown_handler)
    signal.signal(signal.SIGTERM, shutdown_handler)
    
    # Start HTTP server
    logger.info(f"Starting ice-test server on port {port}")
    try:
        app.run(host='0.0.0.0', port=port, debug=False, threaded=True)
    finally:
        if client:
            client.destroy()


if __name__ == '__main__':
    main()

