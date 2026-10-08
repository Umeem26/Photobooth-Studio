import { useBooth } from '../app/context';
import { StepPill } from '../components/ui';
import { paymentEnabled, stepFor, stepsFor, type Screen } from '../state/machine';

/** StepPill sesuai alur: 3 langkah, atau 4 (Layout, Pay, Photos, Result) bila pembayaran aktif. */
export function FlowSteps({ screen }: { screen: Screen }) {
  const { state } = useBooth();
  const withPayment = paymentEnabled(state);
  const active = stepFor(screen, withPayment);
  return active ? <StepPill steps={stepsFor(withPayment)} active={active} /> : null;
}
