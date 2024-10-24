/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import {
  UrlbarProvider,
  UrlbarUtils,
} from "resource:///modules/UrlbarUtils.sys.mjs";

const lazy = {};

ChromeUtils.defineESModuleGetters(lazy, {
  QuickSuggest: "resource:///modules/QuickSuggest.sys.mjs",
  UrlbarPrefs: "resource:///modules/UrlbarPrefs.sys.mjs",
  UrlbarProviderTopSites: "resource:///modules/UrlbarProviderTopSites.sys.mjs",
});

const TELEMETRY_PREFIX = "contextual.services.quicksuggest";

const TELEMETRY_SCALARS = {
  BLOCK: `${TELEMETRY_PREFIX}.block_weather`,
  CLICK: `${TELEMETRY_PREFIX}.click_weather`,
  IMPRESSION: `${TELEMETRY_PREFIX}.impression_weather`,
};

/**
 * A provider that returns a suggested url to the user based on what
 * they have currently typed so they can navigate directly.
 *
 * This provider is active only when either the Rust backend is disabled or
 * weather keywords are defined in Nimbus. When Rust is enabled and keywords are
 * not defined in Nimbus, the Rust component serves the initial weather
 * suggestion and UrlbarProviderQuickSuggest handles it along with other
 * suggestion types. Once the Rust backend is enabled by default and we no
 * longer want to experiment with weather keywords, this provider can be removed
 * along with the legacy telemetry it records.
 */
class ProviderWeather extends UrlbarProvider {
  /**
   * Returns the name of this provider.
   *
   * @returns {string} the name of this provider.
   */
  get name() {
    return "Weather";
  }

  /**
   * The type of the provider.
   *
   * @returns {UrlbarUtils.PROVIDER_TYPE}
   */
  get type() {
    return UrlbarUtils.PROVIDER_TYPE.NETWORK;
  }

  /**
   * @returns {object} An object mapping from mnemonics to scalar names.
   */
  get TELEMETRY_SCALARS() {
    return { ...TELEMETRY_SCALARS };
  }

  getPriority(context) {
    if (!context.searchString) {
      // Zero-prefix suggestions have the same priority as top sites.
      return lazy.UrlbarProviderTopSites.PRIORITY;
    }
    return super.getPriority(context);
  }

  /**
   * Whether this provider should be invoked for the given context.
   * If this method returns false, the providers manager won't start a query
   * with this provider, to save on resources.
   *
   * @param {UrlbarQueryContext} queryContext The query context object
   * @returns {boolean} Whether this provider should be invoked for the search.
   */
  isActive(queryContext) {
    // When Rust is enabled and keywords are not defined in Nimbus, weather
    // results are created by the quick suggest provider, not this one.
    if (
      lazy.UrlbarPrefs.get("quickSuggestRustEnabled") &&
      !lazy.QuickSuggest.weather?.keywords
    ) {
      return false;
    }

    // If the sources don't include search or the user used a restriction
    // character other than search, don't allow any suggestions.
    if (
      !queryContext.sources.includes(UrlbarUtils.RESULT_SOURCE.SEARCH) ||
      (queryContext.restrictSource &&
        queryContext.restrictSource != UrlbarUtils.RESULT_SOURCE.SEARCH)
    ) {
      return false;
    }

    if (queryContext.isPrivate || queryContext.searchMode) {
      return false;
    }

    let { keywords } = lazy.QuickSuggest.weather;
    if (!keywords) {
      return false;
    }

    return keywords.has(queryContext.trimmedLowerCaseSearchString);
  }

  /**
   * Starts querying. Extended classes should return a Promise resolved when the
   * provider is done searching AND returning results.
   *
   * @param {UrlbarQueryContext} queryContext The query context object
   * @param {Function} addCallback Callback invoked by the provider to add a new
   *        result. A UrlbarResult should be passed to it.
   * @returns {Promise}
   */
  async startQuery(queryContext, addCallback) {
    // As a reminder, this provider is not used and this method is not called
    // when Rust is enabled. UrlbarProviderQuickSuggest handles weather
    // suggestions when Rust is enabled.

    let result = await lazy.QuickSuggest.weather.makeResult(
      queryContext,
      null,
      queryContext.searchString
    );
    if (result) {
      result.payload.source = "merino";
      result.payload.provider = "accuweather";
      addCallback(this, result);
    }
  }

  getResultCommands(result) {
    return lazy.QuickSuggest.weather.getResultCommands(result);
  }

  /**
   * This is called only for dynamic result types, when the urlbar view updates
   * the view of one of the results of the provider.  It should return an object
   * describing the view update.
   *
   * @param {UrlbarResult} result
   *   The result whose view will be updated.
   * @returns {object} An object describing the view update.
   */
  getViewUpdate(result) {
    return lazy.QuickSuggest.weather.getViewUpdate(result);
  }

  onEngagement(queryContext, controller, details) {
    this.#sessionResult = details.result;
    this.#engagementSelType = details.selType;

    this.#handlePossibleCommand(
      controller.view,
      details.result,
      details.selType
    );
  }

  onImpression(state, queryContext, controller, providerVisibleResults) {
    this.#sessionResult = providerVisibleResults[0].result;
  }

  onSearchSessionEnd(queryContext, _controller) {
    if (this.#sessionResult) {
      this.#recordEngagementTelemetry(
        this.#sessionResult,
        queryContext.isPrivate,
        this.#engagementSelType
      );
    }

    this.#sessionResult = null;
    this.#engagementSelType = null;
  }

  /**
   * Records engagement telemetry. This should be called only at the end of an
   * engagement when a weather result is present or when a weather result is
   * dismissed.
   *
   * @param {UrlbarResult} result
   *   The weather result that was present (and possibly picked) at the end of
   *   the engagement or that was dismissed.
   * @param {boolean} isPrivate
   *   Whether the engagement is in a private context.
   * @param {string} selType
   *   This parameter indicates the part of the row the user picked, if any, and
   *   should be one of the following values:
   *
   *   - "": The user didn't pick the row or any part of it
   *   - "weather": The user picked the main part of the row
   *   - "dismiss": The user dismissed the result
   *
   *   An empty string means the user picked some other row to end the
   *   engagement, not the weather row. In that case only impression telemetry
   *   will be recorded.
   *
   *   A non-empty string means the user picked the weather row or some part of
   *   it, and both impression and click telemetry will be recorded. The
   *   non-empty-string values come from the `details.selType` passed in to
   *   `onEngagement()`; see `TelemetryEvent.typeFromElement()`.
   */
  #recordEngagementTelemetry(result, isPrivate, selType) {
    // Indexes recorded in quick suggest telemetry are 1-based, so add 1 to the
    // 0-based `result.rowIndex`.
    let telemetryResultIndex = result.rowIndex + 1;

    // impression scalars
    Services.telemetry.keyedScalarAdd(
      TELEMETRY_SCALARS.IMPRESSION,
      telemetryResultIndex,
      1
    );

    // scalars related to clicking the result and other elements in its row
    let clickScalars = [];
    switch (selType) {
      case "weather":
        clickScalars.push(TELEMETRY_SCALARS.CLICK);
        break;
      case "dismiss":
        clickScalars.push(TELEMETRY_SCALARS.BLOCK);
        break;
      default:
        break;
    }
    for (let scalar of clickScalars) {
      Services.telemetry.keyedScalarAdd(scalar, telemetryResultIndex, 1);
    }
  }

  #handlePossibleCommand(view, result, selType) {
    lazy.QuickSuggest.weather.handleCommand(view, result, selType);
  }

  #sessionResult;
  #engagementSelType;
}

export var UrlbarProviderWeather = new ProviderWeather();
