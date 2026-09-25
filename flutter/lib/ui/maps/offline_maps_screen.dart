import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';

import '../../l10n/app_localizations.dart';

/// Offline map viewer using OpenStreetMap tiles.
///
/// Port of `ui/maps/OfflineMapsActivity.kt`, which used OSMDroid.
/// `flutter_map` is the Flutter equivalent, with the same tile source
/// (OpenStreetMap / Mapnik).
class OfflineMapsScreen extends StatefulWidget {
  const OfflineMapsScreen({super.key});

  /// Default center point — same as the Kotlin original (Hargeisa, Somalia).
  static const _defaultCenter = LatLng(2.0593708, 45.236624);
  static const _defaultZoom = 15.0;

  @override
  State<OfflineMapsScreen> createState() => _OfflineMapsScreenState();
}

class _OfflineMapsScreenState extends State<OfflineMapsScreen> {
  late final MapController _mapController;
  LatLng _center = OfflineMapsScreen._defaultCenter;
  double _zoom = OfflineMapsScreen._defaultZoom;

  @override
  void initState() {
    super.initState();
    _mapController = MapController();
  }

  @override
  void dispose() {
    _mapController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.offlineMaps),
        actions: [
          IconButton(
            icon: const Icon(Icons.my_location),
            tooltip: l10n.centerOnDefault,
            onPressed: _centerOnDefault,
          ),
        ],
      ),
      body: FlutterMap(
        mapController: _mapController,
        options: MapOptions(
          initialCenter: _center,
          initialZoom: _zoom,
          onPositionChanged: (position, hasGesture) {
            if (hasGesture) {
              setState(() {
                _center = position.center;
                _zoom = position.zoom;
              });
            }
          },
        ),
        children: [
          TileLayer(
            urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
            userAgentPackageName: 'org.ole.planet.myplanet',
          ),
        ],
      ),
      floatingActionButton: Column(
        mainAxisAlignment: MainAxisAlignment.end,
        children: [
          FloatingActionButton.small(
            heroTag: 'zoom_in',
            onPressed: _zoomIn,
            child: const Icon(Icons.add),
          ),
          const SizedBox(height: 8),
          FloatingActionButton.small(
            heroTag: 'zoom_out',
            onPressed: _zoomOut,
            child: const Icon(Icons.remove),
          ),
        ],
      ),
    );
  }

  void _zoomIn() {
    _mapController.move(_center, _zoom + 1);
  }

  void _zoomOut() {
    _mapController.move(_center, _zoom - 1);
  }

  void _centerOnDefault() {
    _mapController.move(
      OfflineMapsScreen._defaultCenter,
      OfflineMapsScreen._defaultZoom,
    );
  }
}
