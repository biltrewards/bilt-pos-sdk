// The companion display against the SDK double: the empty state, a rendering on the
// lane-banner surface, an offer applied as a discount, and pause/resume.
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { fx, renderApp } from './harness';

describe('the companion display', () => {
  it('says plainly that nothing has been served and mirrors the basket', async () => {
    await renderApp({ tab: 'display' });
    expect(screen.getByTestId('no-creatives').textContent).toContain('No creatives served yet');
    expect(screen.getByTestId('widget-status').textContent).toBe('live');
    expect(screen.getByText('Welcome')).toBeTruthy();

    fireEvent.click(screen.getByRole('tab', { name: 'Sale' }));
    fireEvent.click(screen.getByRole('button', { name: /Coffee/ }));
    fireEvent.click(screen.getByRole('tab', { name: 'Companion display' }));
    await waitFor(() => expect(screen.getByText('1 × Coffee')).toBeTruthy());
  });

  it('draws a rendering, turns an accepted offer into a line discount and pauses the widget', async () => {
    const { session } = await renderApp({ tab: 'display' });
    fireEvent.click(screen.getByRole('tab', { name: 'Sale' }));
    fireEvent.click(screen.getByRole('button', { name: /Umbrella/ }));
    await waitFor(() => expect(screen.getByTestId('grand-total').textContent).toContain('15.98'));
    fireEvent.click(screen.getByRole('tab', { name: 'Companion display' }));

    const rendering = fx.rendering('lane-banner');
    session().emitter.emit('widget.rendering', {
      widget: 'retail-media',
      placement: 'lane-banner',
      rendering,
    });
    await waitFor(() => expect(screen.getByText('Save $1 today')).toBeTruthy());
    expect(screen.getByText(/Showing creative/).textContent).toContain('crt_1 (IMAGE), TTL PT30S');

    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));
    const toast = await screen.findByText(/Offer ofr_1: \$1\.00 off/);
    expect(toast).toBeTruthy();
    expect(screen.getByText(/ofr_1/, { selector: 'code' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Apply as discount' }));
    await waitFor(() =>
      expect(session().basket.current.items[0]?.discounts).toEqual([
        { reference: 'ofr_1', label: 'Offer ofr_1', amount: '1.00' },
      ]),
    );
    await screen.findByText(/Offer applied/);

    fireEvent.click(screen.getByRole('button', { name: /Pause widget/ }));
    await waitFor(() => expect(screen.getByTestId('widget-status').textContent).toBe('paused'));
    expect(session().widget('retail-media').isPaused()).toBe(true);
    // Pausing clears the placement.
    await waitFor(() => expect(screen.getByTestId('no-creatives')).toBeTruthy());
    fireEvent.click(screen.getByRole('button', { name: 'Resume widget' }));
    await waitFor(() => expect(screen.getByTestId('widget-status').textContent).toBe('live'));
  });

  it('explains when the session carries no widget', async () => {
    await renderApp({ tab: 'display', settings: { retailMedia: false } });
    expect(screen.getByText(/Turn retail media on in the Settings tab/)).toBeTruthy();
  });
});
