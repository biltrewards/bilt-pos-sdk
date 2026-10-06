import type { Offer } from '@bilt/pos-protocol';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { RetailMediaSurface } from '../src/index';
import { clearPlacement, showRendering, startLane, type MockTerminalSession } from './harness';

type Observer = { callback: IntersectionObserverCallback; observed: Element[] };

function installIntersectionObserver(): Observer[] {
  const observers: Observer[] = [];
  class FakeObserver {
    observed: Element[] = [];
    constructor(private readonly callback: IntersectionObserverCallback) {
      observers.push({ callback, observed: this.observed });
    }
    observe(element: Element) {
      this.observed.push(element);
    }
    disconnect() {}
    unobserve() {}
    takeRecords() {
      return [];
    }
  }
  vi.stubGlobal('IntersectionObserver', FakeObserver);
  return observers;
}

function intersect(observer: Observer, ratio: number) {
  const entry = {
    isIntersecting: ratio > 0,
    intersectionRatio: ratio,
    target: observer.observed[0]!,
  } as IntersectionObserverEntry;
  observer.callback([entry], {} as IntersectionObserver);
}

describe('RetailMediaSurface', () => {
  let session: MockTerminalSession;

  beforeEach(async () => {
    vi.useFakeTimers();
    session = await startLane();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('renders an empty container until a rendering arrives for its placement', () => {
    const { container } = render(
      <RetailMediaSurface session={session} placement="lane-banner" placeholder="—" />,
    );
    const surface = container.firstElementChild as HTMLElement;
    expect(surface.dataset.state).toBe('empty');
    expect(surface.dataset.placement).toBe('lane-banner');
    expect(surface.textContent).toBe('—');

    act(() => void showRendering(session, 'pin-pad'));
    expect(surface.dataset.state).toBe('empty');

    act(() => void showRendering(session, 'lane-banner'));
    expect(surface.dataset.state).toBe('rendering');
    expect(surface.dataset.media).toBe('IMAGE');
    expect(screen.getByRole('img').getAttribute('src')).toBe('https://cdn.example/crt_1.png');
    expect(screen.getByText('Save $1 today')).toBeTruthy();

    act(() => clearPlacement(session, 'lane-banner'));
    expect(surface.dataset.state).toBe('empty');
  });

  it('renders a video with its poster and reports completed when it ends', () => {
    const completed = vi.spyOn(session.widget('retail-media'), 'completed');
    const { container } = render(<RetailMediaSurface session={session} placement="lane-banner" />);
    const rendering = { ...showRendering(session, 'lane-banner') };
    act(
      () =>
        void showRendering(session, 'lane-banner', {
          media: {
            type: 'VIDEO',
            url: 'https://cdn.example/crt_1.mp4',
            poster: 'https://cdn.example/crt_1.jpg',
            duration: 'PT15S',
          },
        }),
    );
    const video = container.querySelector('video')!;
    expect(video.getAttribute('src')).toBe('https://cdn.example/crt_1.mp4');
    expect(video.getAttribute('poster')).toBe('https://cdn.example/crt_1.jpg');
    expect(video.muted).toBe(true);
    expect(video.hasAttribute('autoplay')).toBe(true);
    expect(video.hasAttribute('playsinline')).toBe(true);

    fireEvent.ended(video);
    expect(completed).toHaveBeenCalledTimes(1);
    expect(completed.mock.calls[0]![0]).toMatchObject({
      creativeId: rendering.creativeId,
      media: { type: 'VIDEO' },
    });
  });

  it('renders an HTML creative in a sandboxed iframe', () => {
    const { container } = render(<RetailMediaSurface session={session} placement="lane-banner" />);
    act(
      () =>
        void showRendering(session, 'lane-banner', {
          media: { type: 'HTML', url: 'https://cdn.example/crt_1.html' },
        }),
    );
    const frame = container.querySelector('iframe')!;
    expect(frame.getAttribute('src')).toBe('https://cdn.example/crt_1.html');
    expect(frame.getAttribute('sandbox')).toBe('allow-scripts');
    expect(frame.getAttribute('referrerpolicy')).toBe('no-referrer');
  });

  it('posts perform for a CTA tap and the host answers with an offer', () => {
    const widget = session.widget('retail-media');
    const perform = vi.spyOn(widget, 'perform');
    const offers: Offer[] = [];
    session.on('widget.offer', ({ offer }) => offers.push(offer));
    render(<RetailMediaSurface session={session} placement="lane-banner" />);
    let rendering = showRendering(session, 'lane-banner');
    act(() => {
      rendering = showRendering(session, 'lane-banner', {
        secondary: { label: 'Details', action: 'DETAILS', token: 'tok_2' },
      });
    });

    fireEvent.click(screen.getByText('Apply'));
    expect(perform).toHaveBeenCalledWith(rendering, rendering.cta);
    expect(offers.map((o) => o.creativeId)).toEqual(['crt_1']);

    fireEvent.click(screen.getByText('Details'));
    expect(perform).toHaveBeenLastCalledWith(rendering, rendering.secondary);
  });

  it('reports viewed once after viewabilityMs without an IntersectionObserver', () => {
    const viewed = vi.spyOn(session.widget('retail-media'), 'viewed');
    render(<RetailMediaSurface session={session} placement="lane-banner" viewabilityMs={500} />);
    act(() => void showRendering(session, 'lane-banner'));
    act(() => void vi.advanceTimersByTime(499));
    expect(viewed).not.toHaveBeenCalled();
    act(() => void vi.advanceTimersByTime(1));
    expect(viewed).toHaveBeenCalledTimes(1);
    act(() => void vi.advanceTimersByTime(5000));
    expect(viewed).toHaveBeenCalledTimes(1);
  });

  it('reports viewed again when the same creative is displayed after a clear', () => {
    const viewed = vi.spyOn(session.widget('retail-media'), 'viewed');
    render(<RetailMediaSurface session={session} placement="lane-banner" viewabilityMs={500} />);
    act(() => void showRendering(session, 'lane-banner'));
    act(() => void vi.advanceTimersByTime(500));
    expect(viewed).toHaveBeenCalledTimes(1);

    act(() => void clearPlacement(session, 'lane-banner'));
    act(() => void showRendering(session, 'lane-banner'));
    act(() => void vi.advanceTimersByTime(500));
    expect(viewed).toHaveBeenCalledTimes(2);
  });

  it('counts viewability only while intersecting when an IntersectionObserver exists', () => {
    const observers = installIntersectionObserver();
    const viewed = vi.spyOn(session.widget('retail-media'), 'viewed');
    render(<RetailMediaSurface session={session} placement="lane-banner" />);
    act(() => void showRendering(session, 'lane-banner'));
    const observer = observers[0]!;
    expect(observer.observed).toHaveLength(1);

    act(() => void vi.advanceTimersByTime(2000));
    expect(viewed).not.toHaveBeenCalled();

    act(() => intersect(observer, 1));
    act(() => void vi.advanceTimersByTime(600));
    act(() => intersect(observer, 0));
    act(() => void vi.advanceTimersByTime(1000));
    expect(viewed).not.toHaveBeenCalled();

    act(() => intersect(observer, 0.8));
    act(() => void vi.advanceTimersByTime(1000));
    expect(viewed).toHaveBeenCalledTimes(1);
  });

  it('reports dismissed from its own close control and clears the surface', () => {
    const dismissed = vi.spyOn(session.widget('retail-media'), 'dismissed');
    const onRendering = vi.fn();
    const { container } = render(
      <RetailMediaSurface
        session={session}
        placement="lane-banner"
        dismissible
        onRendering={onRendering}
      />,
    );
    act(() => void showRendering(session, 'lane-banner'));
    fireEvent.click(screen.getByLabelText('Dismiss'));
    expect(dismissed).toHaveBeenCalledTimes(1);
    expect((container.firstElementChild as HTMLElement).dataset.state).toBe('empty');
    expect(onRendering).toHaveBeenLastCalledWith(null);
  });

  it('clears when the session ends and stays empty without a session', () => {
    const { container, rerender } = render(
      <RetailMediaSurface session={session} placement="lane-banner" />,
    );
    act(() => void showRendering(session, 'lane-banner'));
    act(() => void session.end());
    expect((container.firstElementChild as HTMLElement).dataset.state).toBe('empty');
    rerender(<RetailMediaSurface session={null} placement="lane-banner" />);
    expect((container.firstElementChild as HTMLElement).dataset.state).toBe('empty');
  });

  it('tells onRendering when the session ends or changes, once per display', async () => {
    const other = await startLane();
    const onRendering = vi.fn();
    const { rerender } = render(
      <RetailMediaSurface session={session} placement="lane-banner" onRendering={onRendering} />,
    );
    expect(onRendering).not.toHaveBeenCalled();
    act(() => void showRendering(session, 'lane-banner'));
    act(() => void session.end());
    expect(onRendering).toHaveBeenLastCalledWith(null);
    expect(onRendering).toHaveBeenCalledTimes(2);

    act(() => void showRendering(other, 'lane-banner'));
    rerender(
      <RetailMediaSurface session={other} placement="lane-banner" onRendering={onRendering} />,
    );
    rerender(
      <RetailMediaSurface session={session} placement="lane-banner" onRendering={onRendering} />,
    );
    expect(onRendering).toHaveBeenCalledTimes(2);
  });
});
