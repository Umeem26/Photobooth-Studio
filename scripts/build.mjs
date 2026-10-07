// npm run build: JAR sidecar (tanpa test; test lewat ./mvnw verify) + build UI ke ui/dist.
import { buildJar, buildUi } from './lib.mjs';

await buildJar();
await buildUi();
console.log('Build selesai: target/vandebooth.jar dan ui/dist/');
