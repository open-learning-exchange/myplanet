import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';

/// A [PlanetApi] double for tests that construct a repository but never sync.
///
/// Phase 119 gave `RatingsRepository`, `TeamTasksRepository` and
/// `AchievementsRepository` their missing sync-in walks, so each now takes an
/// api. An unstubbed mocktail double throws on any call, which is what these
/// tests want: reaching the network from a local-only test should fail loudly.
class MockPlanetApi extends Mock implements PlanetApi {}
