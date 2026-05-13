package com.jvn.editor.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/**
 * Lightweight CSS-only icon factory using SVG path shapes rendered
 * via {@code -fx-shape} on a tiny {@link Region}. No image assets needed.
 */
public final class CssIcon {
  private CssIcon() {}

  // ── SVG path data ──

    private static final String PATH_SPARKLES = "M19 8.3q-.125 0-.262-.075Q18.6 8.15 18.55 8l-.8-1.75-1.75-.8q-.15-.05-.225-.188Q15.7 5.125 15.7 5t.075-.263Q15.85 4.6 16 4.55l1.75-.8.8-1.75q.05-.15.188-.225.137-.075.262-.075t.263.075q.137.075.187.225l.8 1.75 1.75.8q.15.05.225.187.075.138.075.263t-.075.262Q22.15 5.4 22 5.45l-1.75.8-.8 1.75q-.05.15-.187.225-.138.075-.263.075Zm0 14q-.125 0-.262-.075-.138-.075-.188-.225l-.8-1.75-1.75-.8q-.15-.05-.225-.188-.075-.137-.075-.262t.075-.262q.075-.138.225-.188l1.75-.8.8-1.75q.05-.15.188-.225.137-.075.262-.075t.263.075q.137.075.187.225l.8 1.75 1.75.8q.15.05.225.188.075.137.075.262t-.075.262q-.075.138-.225.188l-1.75.8-.8 1.75q-.05.15-.187.225-.138.075-.263.075ZM9 18.575q-.275 0-.525-.15T8.1 18l-1.6-3.5L3 12.9q-.275-.125-.425-.375-.15-.25-.15-.525t.15-.525q.15-.25.425-.375l3.5-1.6L8.1 6q.125-.275.375-.425.25-.15.525-.15t.525.15q.25.15.375.425l1.6 3.5 3.5 1.6q.275.125.425.375.15.25.15.525t-.15.525q-.15.25-.425.375l-3.5 1.6L9.9 18q-.125.275-.375.425-.25.15-.525.15Zm0-3.425L10 13l2.15-1L10 11 9 8.85 8 11l-2.15 1L8 13ZM9 12Z";
  private static final String PATH_THEATER = "M21 2h-8c-1.1 0-2 .9-2 2v3.5h1.5c1.1 0 2 .9 2 2v4.95c1.04.48 2.24.68 3.5.47 2.93-.49 5-3.17 5-6.14V4c0-1.1-.9-2-2-2zm-7 4.5c0-.55.45-1 1-1s1 .45 1 1-.45 1-1 1-1-.45-1-1zm4.85 4.38h-3.72c-.38 0-.63-.41-.44-.75.39-.66 1.27-1.13 2.3-1.13s1.91.47 2.3 1.14c.19.33-.06.74-.44.74zM19 7.5c-.55 0-1-.45-1-1s.45-1 1-1 1 .45 1 1-.45 1-1 1z M11 9H3c-1.1 0-2 .9-2 2v4.79c0 3.05 2.19 5.77 5.21 6.16C9.87 22.42 13 19.57 13 16v-5c0-1.1-.9-2-2-2zm-7 4.5c0-.55.45-1 1-1s1 .45 1 1-.45 1-1 1-1-.45-1-1zm5.3 3.25c-.38.67-1.27 1.14-2.3 1.14s-1.91-.47-2.3-1.14c-.19-.34.06-.75.44-.75h3.72c.38 0 .63.41.44.75zM9 14.5c-.55 0-1-.45-1-1s.45-1 1-1 1 .45 1 1-.45 1-1 1z";
  private static final String PATH_ROBOT = "M20 9V7c0-1.1-.9-2-2-2h-3c0-1.66-1.34-3-3-3S9 3.34 9 5H6c-1.1 0-2 .9-2 2v2c-1.66 0-3 1.34-3 3s1.34 3 3 3v4c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2v-4c1.66 0 3-1.34 3-3s-1.34-3-3-3zM7.5 11.5c0-.83.67-1.5 1.5-1.5s1.5.67 1.5 1.5S9.83 13 9 13s-1.5-.67-1.5-1.5zM15 17H9c-.55 0-1-.45-1-1s.45-1 1-1h6c.55 0 1 .45 1 1s-.45 1-1 1zm0-4c-.83 0-1.5-.67-1.5-1.5S14.17 10 15 10s1.5.67 1.5 1.5S15.83 13 15 13z";
  private static final String PATH_LIGHTBULB = "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm-3-3h6c.55 0 1-.45 1-1s-.45-1-1-1H9c-.55 0-1 .45-1 1s.45 1 1 1zm3-17C7.86 2 4.5 5.36 4.5 9.5c0 3.82 2.66 5.86 3.77 6.5h7.46c1.11-.64 3.77-2.68 3.77-6.5C19.5 5.36 16.14 2 12 2z";
  private static final String PATH_PLUS =
      "M11 19v-6H5v-2h6V5h2v6h6v2h-6v6h-2Z";
  private static final String PATH_PLUS_BOLD =
      "M11 19v-6H5v-2h6V5h2v6h6v2h-6v6h-2Z";
  private static final String PATH_MINUS =
      "M5 13v-2h14v2H5Z";
  private static final String PATH_ARROW_UP =
      "M11 19V6.825l-5.6 5.6L4 11l8-8 8 8-1.4 1.425-5.6-5.6V19h-2Z";
  private static final String PATH_ARROW_DOWN =
      "M11 5v12.175l-5.6-5.6L4 13l8 8 8-8-1.4-1.425-5.6 5.6V5h-2Z";
  private static final String PATH_ARROW_LEFT =
      "M11 19 3 11l8-8 1.425 1.4L6.825 10H21v2H6.825l5.6 5.6L11 19Z";
  private static final String PATH_ARROW_RIGHT =
      "M13 19l-1.425-1.4 5.6-5.6H3v-2h14.175l-5.6-5.6L13 3l8 8-8 8Z";
  private static final String PATH_SORT =
      "M3 18v-2h6v2H3Zm0-5v-2h12v2H3Zm0-5V6h18v2H3Z";
  private static final String PATH_FOLDER =
      "M4 20q-.825 0-1.412-.587Q2 18.825 2 18V6q0-.825.588-1.412Q3.175 4 4 4h6l2 2h8q.825 0 1.413.588Q22 7.175 22 8v10q0 .825-.587 1.413Q20.825 20 20 20H4Z";
  private static final String PATH_CLEAR_X =
      "M6.4 19 5 17.6l5.6-5.6L5 6.4 6.4 5l5.6 5.6L17.6 5 19 6.4 13.4 12l5.6 5.6-1.4 1.4-5.6-5.6L6.4 19Z";
  private static final String PATH_UNDO =
      "M7.05 14q1.225-2.075 3.337-3.287Q12.5 9.5 15 9.5q2.5 0 4.675 1.15T23.55 14H21q-1.2-1.25-2.812-1.875Q16.575 11.5 15 11.5q-1.6 0-3.187.625T9 14h2v2H5V9h2v2.85Z";
  private static final String PATH_REDO =
      "M16.95 14q-1.225-2.075-3.337-3.287Q11.5 9.5 9 9.5q-2.5 0-4.675 1.15T.45 14H3q1.2-1.25 2.813-1.875Q7.425 11.5 9 11.5q1.6 0 3.188.625T15 14h-2v2h6V9h-2v2.85Z";
  private static final String PATH_SPEECH =
      "M4 22q-.825 0-1.412-.587Q2 20.825 2 20V4q0-.825.588-1.412Q3.175 2 4 2h16q.825 0 1.413.588Q22 3.175 22 4v12q0 .825-.587 1.413Q20.825 18 20 18H8l-4 4Z";
  private static final String PATH_LIST =
      "M3 14v-2h2v2H3Zm0-4V8h2v2H3Zm0-4V4h2v2H3Zm4 8v-2h14v2H7Zm0-4V8h14v2H7Zm0-4V4h14v2H7Z";
  private static final String PATH_SEARCH =
      "M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z";
  private static final String PATH_GRID =
      "M4 20q-.825 0-1.412-.587Q2 18.825 2 18v-5h9v7H4Zm11 0v-7h9v5q0 .825-.587 1.413Q22.825 20 22 20h-7ZM2 11V6q0-.825.588-1.412Q3.175 4 4 4h7v7H2Zm11 0V4h7q.825 0 1.413.588Q22 5.175 22 6v5h-9Z";
  private static final String PATH_DOWNLOAD =
      "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z";
  private static final String PATH_SAVE =
      "M17 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4zm-5 16c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm3-10H5V5h10v4z";
  private static final String PATH_EXPAND =
      "M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z";
  private static final String PATH_PALETTE =
      "M12 22q-1.25 0-2.125-.875T9 19q0-.45.163-.863.162-.412.512-.862.5-.55.863-1.163.362-.612.362-1.362 0-1.125-.8-1.937Q9.3 12 8.15 12H4.55Q3.5 12 2.75 11.25T2 9V7q0-2.075 1.463-3.538Q4.925 2 7 2h10q2.075 0 3.538 1.462Q22 4.925 22 7v5q0 2.075-1.462 3.537Q19.075 17 17 17h-1.5q-.625 0-1.062.438-.438.437-.438 1.062 0 .425.2.825.2.4.525.875.475.675.763 1.337.287.663.287 1.463 0 1.25-.875 2.125T12 22Z";
  private static final String PATH_WARNING =
      "M12 22q-.825 0-1.412-.587Q10 20.825 10 20q0-.825.588-1.413Q11.175 18 12 18q.825 0 1.413.587Q14 19.175 14 20q0 .825-.587 1.413Q13.175 22 12 22Zm-1-6V6h2v10h-2Z";
  private static final String PATH_CHECK =
      "m9.55 18-5.7-5.7 1.425-1.425L9.55 15.15l9.175-9.175L20.15 7.4 9.55 18Z";
  private static final String PATH_ERROR =
      "M12 22q-2.075 0-3.9-.787-1.825-.788-3.175-2.138-1.35-1.35-2.137-3.175Q2 14.075 2 12t.788-3.9q.787-1.825 2.137-3.175 1.35-1.35 3.175-2.137Q9.925 2 12 2t3.9.788q1.825.787 3.175 2.137 1.35 1.35 2.138 3.175Q22 9.925 22 12t-.787 3.9q-.788 1.825-2.138 3.175-1.35 1.35-3.175 2.138Q14.075 22 12 22Zm0-2q3.35 0 5.675-2.325Q20 15.35 20 12q0-3.35-2.325-5.675Q15.35 4 12 4 8.65 4 6.325 6.325 4 8.65 4 12q0 3.35 2.325 5.675Q8.65 20 12 20Zm-1-5h2v-2h-2v2Zm0-4h2V5h-2v6Z";
  private static final String PATH_LINK =
      "M7 15q-1.25 0-2.125-.875T4 12q0-1.25.875-2.125T7 9h4V7H7q-2.075 0-3.537 1.463Q2 9.925 2 12q0 2.075 1.463 3.538Q4.925 17 7 17h4v-2H7Zm1.5-2v-2h7v2h-7Zm4.5 4v-2h4q1.25 0 2.125-.875T20 12q0-1.25-.875-2.125T17 9h-4V7h4q2.075 0 3.538 1.463Q22 9.925 22 12q0 2.075-1.462 3.538Q19.075 17 17 17h-4Z";
  private static final String PATH_HOME =
      "M4 21V9l8-6 8 6v12h-6v-7h-4v7H4Z";
  private static final String PATH_COPY =
      "M9 18q-.825 0-1.412-.587Q7 16.825 7 16V4q0-.825.588-1.412Q8.175 2 9 2h9q.825 0 1.413.588Q20 3.175 20 4v12q0 .825-.587 1.413Q18.825 18 18 18H9Zm0-2h9V4H9v12Zm-4 6q-.825 0-1.412-.587Q3 20.825 3 20V6h2v14h11v2H5Zm4-6V4v12Z";
  private static final String PATH_PLAY =
      "M8 19V5l11 7-11 7Z";
  private static final String PATH_STOP =
      "M6 18V6h12v12H6Z";
  private static final String PATH_POP_OUT =
      "M19 19H5V5h7V3H5q-.825 0-1.412.588Q3 4.175 3 5v14q0 .825.588 1.413Q4.175 21 5 21h14q.825 0 1.413-.587Q21 19.825 21 19v-7h-2v7ZM14 3v2h3.59l-9.83 9.83 1.41 1.41L19 6.41V10h2V3h-7Z";
  private static final String PATH_DOCK =
      "M18 20V4H6v16h12ZM6 2h12q.825 0 1.413.588Q20 3.175 20 4v16q0 .825-.587 1.413Q18.825 22 18 22H6q-.825 0-1.412-.587Q4 20.825 4 20V4q0-.825.588-1.412Q5.175 2 6 2ZM8 18h8v-2H8v2Zm-2 2V4v16Z";
  private static final String PATH_RECT_SELECT =
      "M3 21v-4h2v2h2v2H3Zm6 0v-2h2v2H9Zm4 0v-2h2v2h-2Zm4 0v-2h2v-2h2v4h-4ZM3 15v-2h2v2H3Zm16 0v-2h2v2h-2ZM3 11V9h2v2H3Zm16 0V9h2v2h-2ZM3 7V3h4v2H5v2H3Zm6-2V3h2v2H9Zm4 0V3h2v2h-2Zm4 2V3h4v4h-2V5h-2v2h-2Z";
  private static final String PATH_POLYGON =
      "M18.5 22q-1.45 0-2.475-1.025Q15 19.95 15 18.5q0-.325.063-.612.062-.288.162-.563l-5.05-3.05q-.4.325-.887.475-.488.15-.988.15-1.45 0-2.475-1.025Q4.8 12.85 4.8 11.4q0-.4.1-.763.1-.362.25-.662L2.7 6.375Q2.35 6.025 2.175 5.563 2 5.1 2 4.5 2 3.05 3.025 2.025 4.05 1 5.5 1q1.45 0 2.475 1.025Q9 3.05 9 4.5q0 .425-.1.812-.1.388-.3.738l2.425 3.525q.425-.325.925-.45.5-.125 1.05-.125 1.45 0 2.475 1.025Q16.5 11.05 16.5 12.5q0 .175-.025.325t-.05.275l4.8 4.25q.35-.2.725-.275.375-.075.75-.075 1.45 0 2.475 1.025Q26.2 19.05 26.2 20.5q0 1.45-1.025 2.475Q24.15 24 22.7 24q-1.45 0-2.475-1.025Q19.2 21.95 19.2 20.5q0-.35.075-.688.075-.337.25-.662l-4.75-4.225q-.4.25-.837.375-.438.125-.938.125-.35 0-.675-.075t-.65-.2L16.8 18.25q.1.25.15.525.05.275.05.575 0 1.45-1.025 2.475Q15.95 23 14.5 23q-1.45 0-2.475-1.025Q11 20.95 11 19.5q0-.325.075-.6.075-.275.225-.525l-2.45-3.575q-.35.175-.712.238-.363.062-.738.062ZM5.5 3q-.625 0-1.062.438Q4 3.875 4 4.5q0 .625.438 1.062Q4.875 6 5.5 6q.625 0 1.062-.438Q7 5.125 7 4.5q0-.625-.438-1.062Q6.125 3 5.5 3ZM13 12.5q0-.625-.438-1.062Q12.125 11 11.5 11q-.625 0-1.062.438-.438.437-.438 1.062 0 .625.438 1.062.437.438 1.062.438.625 0 1.062-.438Q13 13.125 13 12.5ZM14.5 21q.625 0 1.062-.438.438-.437.438-1.062 0-.625-.438-1.062-.437-.438-1.062-.438-.625 0-1.062.438-.438.437-.438 1.062 0 .625.438 1.062.437.438 1.062.438Zm8.2-2q.625 0 1.062-.438.438-.437.438-1.062 0-.625-.438-1.062-.437-.438-1.062-.438-.625 0-1.062.438-.438.437-.438 1.062 0 .625.438 1.062.437.438 1.062.438Z";
  private static final String PATH_FREEHAND =
      "M4 19v-2.825l10.875-10.875q.3-.3.725-.3.425 0 .725.3l1.4 1.425q.3.3.3.7 0 .4-.3.7L6.85 19H4Zm13.025-9.425 1.4-1.425-1.4-1.4-1.425 1.4 1.425 1.425Z";
  private static final String PATH_VISIBILITY =
      "M12 17.5q3.325 0 6.075-1.925T21.825 12q-1-3-3.75-4.925T12 5.15q-3.325 0-6.075 1.925T2.175 12q1 3 3.75 4.925T12 17.5Zm0-2q-1.45 0-2.475-1.025Q8.5 13.45 8.5 12q0-1.45 1.025-2.475Q10.55 8.5 12 8.5q1.45 0 2.475 1.025Q15.5 10.55 15.5 12q0 1.45-1.025 2.475Q13.45 15.5 12 15.5ZM12 12Z";
  private static final String PATH_VISIBILITY_OFF =
      "m19.8 22.6-3.8-3.8q-1 .475-2.025.688Q12.95 19.7 12 19.7q-3.325 0-6.075-1.925T2.175 12q.5-1.5 1.35-2.737.85-1.238 2.05-2.263L1.4 2.8l1.4-1.4 18.4 18.4-1.4 1.4Zm-7.8-7.8Zm-1.875 1.875q.65.175 1.312.25.663.075 1.438.075 2.225 0 3.8-1.5T18.3 11.5q0-.7-.112-1.325-.113-.625-.338-1.225l-2.2 2.2q-.1.45-.4 1.025-.3.575-1 .85-.7.275-1.375.113-.675-.163-1.125-.563L10.125 14l-.325.25q.15.175.325.35.175.175.425.25L10.125 14Zm5.275-5.275Z";
  private static final String PATH_MEMORY =
      "M9 21v-2H7q-.825 0-1.412-.587Q5 17.825 5 17v-2H3v-2h2v-2H3V9h2V7q0-.825.588-1.412Q7.175 5 8 5h2V3h2v2h2V3h2v2h2q.825 0 1.413.588Q20 6.175 20 7v2h2v2h-2v2h2v2h-2v2q0 .825-.587 1.413Q18.825 19 18 19h-2v2h-2v-2h-2v2h-2v-2H9v2Zm-2-4h10V7H7v10Zm2-2h6V9H9v6Zm2-2h2v-2h-2v2Z";
  private static final String PATH_PERSON =
      "M12 12q-1.65 0-2.825-1.175Q8 9.65 8 8q0-1.65 1.175-2.825Q10.35 4 12 4q1.65 0 2.825 1.175Q16 6.35 16 8q0 1.65-1.175 2.825Q13.65 12 12 12Zm-8 8v-2.8q0-.85.438-1.562.437-.713 1.162-1.088 1.55-.775 3.15-1.163Q10.35 13 12 13t3.25.387q1.6.388 3.15 1.163.725.375 1.162 1.088Q20 16.35 20 17.2V20H4Z";
  private static final String PATH_LANDSCAPE =
      "M3 21 10.5 11l5.5 7h4L16 11l4-5.5L25.5 21H3ZM6.4 19h15.2l-5.6-7.5-4.5 6-3.1-4-2 5.5Z";
  private static final String PATH_DOCUMENT =
      "M6 22q-.825 0-1.412-.587Q4 20.825 4 20V4q0-.825.588-1.412Q5.175 2 6 2h8l6 6v12q0 .825-.587 1.413Q18.825 22 18 22H6Zm7-13V4H6v16h12V9h-5Z";
  private static final String PATH_EDIT =
      "M5 19h1.4l8.625-8.625-1.4-1.4L5 17.6V19ZM19.3 8.925l-4.25-4.2 1.4-1.4q.575-.575 1.413-.575.837 0 1.412.575l1.4 1.4q.575.575.6 1.388.025.812-.55 1.387L19.3 8.925ZM17.85 10.4 7.25 21H3v-4.25l10.6-10.6 4.25 4.25Z";
  private static final String PATH_DELETE =
      "M7 21q-.825 0-1.412-.587Q5 19.825 5 19V6H4V4h5V3h6v1h5v2h-1v13q0 .825-.587 1.413Q17.825 21 17 21H7ZM17 6H7v13h10V6ZM9 17h2V8H9v9Zm4 0h2V8h-2v9ZM7 6v13V6Z";
  private static final String PATH_TIMELINE =
      "M4 20q-.825 0-1.412-.587Q2 18.825 2 18V6q0-.825.588-1.412Q3.175 4 4 4h16q.825 0 1.413.588Q22 5.175 22 6v12q0 .825-.587 1.413Q20.825 20 20 20H4Zm0-2h16V6H4v12Zm3-2h10v-2H7v2Zm-4-4h2V8H3v4Zm4 0h14V8H7v4ZM4 6v12V6Z";
  private static final String PATH_ROCKET =
      "M14.05 18.3 12 16.25v-3.4l2.05-2.05H17.4l2.05 2.05v3.4l-2.05 2.05h-3.35ZM12 22v-3H8v3h4Zm0-4v-3.4l2.05-2.05h3.35L19.45 14.6v3.4ZM8 14H5v-4h3v4Zm4-4V6.6L9.95 4.55H6.6L4.55 6.6v3.4H8v-4h4Zm12 4v-4h-3v4h3Zm0-4h-3V6.6L18.95 4.55h-3.35L13.55 6.6V10h4v-4h4v4ZM8 6H5V3h3v3Zm12 0h-3V3h3v3Z";
  private static final String PATH_BRANCH_PLUS =
      "M6 4a3 3 0 1 0 2 5.236v2.264c0 2.485 2.015 4.5 4.5 4.5H15a3 3 0 1 0 0-2h-2.5A2.5 2.5 0 0 1 10 11.5V9.236A3 3 0 0 0 6 4Zm0 2a1 1 0 1 1 0 2 1 1 0 0 1 0-2Zm12 7a1 1 0 1 1 0 2 1 1 0 0 1 0-2Zm1-10v3h3v2h-3v3h-2V8h-3V6h3V3h2Z";
  private static final String PATH_MOVIE =
      "M4 20q-.825 0-1.412-.587Q2 18.825 2 18V6q0-.825.588-1.412Q3.175 4 4 4h16q.825 0 1.413.588Q22 5.175 22 6v12q0 .825-.587 1.413Q20.825 20 20 20H4Zm0-2h16V6h-2v2h-2V6h-2v2h-2V6h-2v2H8V6H6v2H4v10Zm6-1l5.5-4-5.5-4v8Z";
  private static final String PATH_LABEL =
      "M17.65 20H4q-.825 0-1.412-.587Q2 18.825 2 18V6q0-.825.588-1.412Q3.175 4 4 4h13.65q.425 0 .8.175.375.175.65.475l4.6 5.35q.35.4.35.925t-.35.925l-4.6 5.35q-.275.3-.65.475-.375.175-.8.175ZM17 18l4.25-5L17 8H4v10h13Z";
  private static final String PATH_AUTO =
      "M10 20l-1.6-5.4L3 13l5.4-1.6L10 6l1.6 5.4L17 13l-5.4 1.6L10 20ZM20 12l-.8-2.2L17 9l2.2-.8L20 6l.8 2.2L23 9l-2.2.8L20 12Zm0-12l-.8 2.2L17 3l2.2.8L20 6l.8-2.2L23 3l-2.2-.8L20 0Z";
  private static final String PATH_SETTINGS =
      "M19.4 13q.05-.25.075-.55.025-.3.025-.5 0-.25-.025-.5t-.075-.55l2.1-1.65q.2-.15.25-.425.05-.275-.05-.525l-2-3.45q-.15-.25-.4-.35-.25-.1-.5-.05l-2.45 1q-.5-.4-1.1-.75-.6-.35-1.3-.55l-.4-2.65q-.05-.25-.25-.45-.2-.2-.45-.2h-4q-.25 0-.45.2-.2.2-.25.45l-.4 2.65q-.7.2-1.3.55-.6.35-1.1.75l-2.45-1q-.25-.05-.5.05-.25.1-.4.35l-2 3.45q-.1.25-.05.525.05.275.25.425l2.1 1.65q-.05.25-.075.55-.025.3-.025.5 0 .25.025.5t.075.55l-2.1 1.65q-.2.15-.25.425-.05.275.05.525l2 3.45q.15.25.4.35.25.1.5.05l2.45-1q.5.4 1.1.75.6.35 1.3.55l.4 2.65q.05.25.25.45.2.2.45.2h4q.25 0 .45-.2.2-.2.25-.45l.4-2.65q.7-.2 1.3-.55.6-.35 1.1-.75l2.45 1q.25.05.5-.05.25-.1.4-.35l2-3.45q.1-.25.05-.525-.05-.275-.25-.425l-2.1-1.65ZM12 15.5q-1.45 0-2.475-1.025Q8.5 13.45 8.5 12q0-1.45 1.025-2.475Q10.55 8.5 12 8.5q1.45 0 2.475 1.025Q15.5 10.55 15.5 12q0 1.45-1.025 2.475Q13.45 15.5 12 15.5Z";

  // ── Factory methods ──

    public static Region sparkles(String color) { return icon(PATH_SPARKLES, color, 14); }
  public static Region theater(String color) { return icon(PATH_THEATER, color, 14); }
  public static Region robot(String color) { return icon(PATH_ROBOT, color, 14); }
  public static Region lightbulb(String color) { return icon(PATH_LIGHTBULB, color, 14); }
  public static Region plus(String color)     { return icon(PATH_PLUS, color, 14); }
  public static Region plusBold(String color) { return icon(PATH_PLUS_BOLD, color, 14); }
  public static Region minus(String color)    { return icon(PATH_MINUS, color, 14); }
  public static Region arrowUp(String color)  { return icon(PATH_ARROW_UP, color, 12); }
  public static Region arrowDown(String color){ return icon(PATH_ARROW_DOWN, color, 12); }
  public static Region arrowLeft(String color){ return icon(PATH_ARROW_LEFT, color, 12); }
  public static Region arrowRight(String color){ return icon(PATH_ARROW_RIGHT, color, 12); }
  public static Region sort(String color)     { return icon(PATH_SORT, color, 14); }
  public static Region folder(String color)   { return icon(PATH_FOLDER, color, 14); }
  public static Region clearX(String color)   { return icon(PATH_CLEAR_X, color, 13); }
  public static Region undo(String color)     { return icon(PATH_UNDO, color, 14); }
  public static Region redo(String color)     { return icon(PATH_REDO, color, 14); }
  public static Region speech(String color)   { return icon(PATH_SPEECH, color, 14); }
  public static Region list(String color)     { return icon(PATH_LIST, color, 14); }
  public static Region search(String color)   { return icon(PATH_SEARCH, color, 14); }
  public static Region grid(String color)     { return icon(PATH_GRID, color, 14); }
  public static Region palette(String color)  { return icon(PATH_PALETTE, color, 14); }
  public static Region warning(String color)  { return icon(PATH_WARNING, color, 14); }
  public static Region download(String color)  { return icon(PATH_DOWNLOAD, color, 14); }
  public static Region save(String color)      { return icon(PATH_SAVE, color, 14); }
  public static Region expand(String color)    { return icon(PATH_EXPAND, color, 14); }
  public static Region check(String color)     { return icon(PATH_CHECK, color, 14); }
  public static Region error(String color)     { return icon(PATH_ERROR, color, 14); }
  public static Region link(String color)      { return icon(PATH_LINK, color, 14); }
  public static Region home(String color)      { return icon(PATH_HOME, color, 14); }
  public static Region copy(String color)      { return icon(PATH_COPY, color, 14); }
  public static Region play(String color)      { return icon(PATH_PLAY, color, 14); }
  public static Region stop(String color)      { return icon(PATH_STOP, color, 12); }
  public static Region runtimePlay()           { return icon(PATH_PLAY, "#83e4a1", 17); }
  public static Region runtimeStop()           { return icon(PATH_STOP, "#f39aaa", 15); }
  public static Region runtimeClear()          { return icon(PATH_CLEAR_X, "#f5c46b", 16); }
  public static Region runtimeCopy()           { return icon(PATH_COPY, "#9ad4ff", 16); }
  public static Region popOut(String color)    { return icon(PATH_POP_OUT, color, 14); }
  public static Region dock(String color)      { return icon(PATH_DOCK, color, 14); }
  public static Region rectSelect(String color) { return icon(PATH_RECT_SELECT, color, 14); }
  public static Region polygon(String color)    { return icon(PATH_POLYGON, color, 14); }
  public static Region freehand(String color)   { return icon(PATH_FREEHAND, color, 14); }
  public static Region visibility(String color) { return icon(PATH_VISIBILITY, color, 14); }
  public static Region visibilityOff(String color) { return icon(PATH_VISIBILITY_OFF, color, 14); }
  public static Region memory(String color) { return icon(PATH_MEMORY, color, 14); }
  public static Region person(String color) { return icon(PATH_PERSON, color, 14); }
  public static Region landscape(String color) { return icon(PATH_LANDSCAPE, color, 14); }
  public static Region document(String color) { return icon(PATH_DOCUMENT, color, 14); }
  public static Region edit(String color) { return icon(PATH_EDIT, color, 14); }
  public static Region delete(String color) { return icon(PATH_DELETE, color, 14); }
  public static Region timeline(String color) { return icon(PATH_TIMELINE, color, 14); }
  public static Region rocket(String color) { return icon(PATH_ROCKET, color, 14); }
  public static Region branchPlus(String color) { return icon(PATH_BRANCH_PLUS, color, 15); }
  public static Region movie(String color) { return icon(PATH_MOVIE, color, 14); }
  public static Region label(String color) { return icon(PATH_LABEL, color, 14); }
  public static Region auto(String color) { return icon(PATH_AUTO, color, 14); }
  public static Region settings(String color) { return icon(PATH_SETTINGS, color, 14); }

  /** Convenience: icon at default muted color. */
    public static Region sparkles() { return sparkles("#b0b8c8"); }
  public static Region theater() { return theater("#b0b8c8"); }
  public static Region robot() { return robot("#b0b8c8"); }
  public static Region lightbulb() { return lightbulb("#b0b8c8"); }
  public static Region plus()     { return plus("#b0b8c8"); }
  public static Region plusBold() { return plusBold("#b0b8c8"); }
  public static Region minus()    { return minus("#b0b8c8"); }
  public static Region arrowUp()  { return arrowUp("#b0b8c8"); }
  public static Region arrowDown(){ return arrowDown("#b0b8c8"); }
  public static Region arrowLeft(){ return arrowLeft("#b0b8c8"); }
  public static Region arrowRight(){ return arrowRight("#b0b8c8"); }
  public static Region sort()     { return sort("#b0b8c8"); }
  public static Region folder()   { return folder("#b0b8c8"); }
  public static Region clearX()   { return clearX("#b0b8c8"); }
  public static Region undo()     { return undo("#b0b8c8"); }
  public static Region redo()     { return redo("#b0b8c8"); }
  public static Region speech()   { return speech("#b0b8c8"); }
  public static Region list()     { return list("#b0b8c8"); }
  public static Region search()   { return search("#b0b8c8"); }
  public static Region grid()     { return grid("#b0b8c8"); }
  public static Region palette()  { return palette("#b0b8c8"); }
  public static Region warning()  { return warning("#b0b8c8"); }
  public static Region download() { return download("#b0b8c8"); }
  public static Region save()     { return save("#b0b8c8"); }
  public static Region expand()   { return expand("#b0b8c8"); }
  public static Region check()    { return check("#b0b8c8"); }
  public static Region error()    { return error("#b0b8c8"); }
  public static Region link()     { return link("#b0b8c8"); }
  public static Region home()     { return home("#b0b8c8"); }
  public static Region copy()     { return copy("#b0b8c8"); }
  public static Region play()     { return play("#b0b8c8"); }
  public static Region stop()     { return stop("#b0b8c8"); }
  public static Region popOut()   { return popOut("#b0b8c8"); }
  public static Region dock()     { return dock("#b0b8c8"); }
  public static Region rectSelect(){ return rectSelect("#b0b8c8"); }
  public static Region polygon()  { return polygon("#b0b8c8"); }
  public static Region freehand() { return freehand("#b0b8c8"); }
  public static Region visibility() { return visibility("#b0b8c8"); }
  public static Region visibilityOff() { return visibilityOff("#b0b8c8"); }
  public static Region memory() { return memory("#b0b8c8"); }
  public static Region person() { return person("#b0b8c8"); }
  public static Region landscape() { return landscape("#b0b8c8"); }
  public static Region document() { return document("#b0b8c8"); }
  public static Region edit() { return edit("#b0b8c8"); }
  public static Region delete() { return delete("#b0b8c8"); }
  public static Region timeline() { return timeline("#b0b8c8"); }
  public static Region rocket() { return rocket("#b0b8c8"); }
  public static Region branchPlus() { return branchPlus("#b0b8c8"); }
  public static Region movie() { return movie("#b0b8c8"); }
  public static Region label() { return label("#b0b8c8"); }
  public static Region auto() { return auto("#b0b8c8"); }
  public static Region settings() { return settings("#b0b8c8"); }

  /**
   * Creates a section header label with a leading CSS icon.
   */
  public static HBox iconLabel(Region icon, String text, String style) {
    Label label = new Label(text);
    label.setStyle(style);
    HBox box = new HBox(6, icon, label);
    box.setAlignment(Pos.CENTER_LEFT);
    return box;
  }

  // ── Core builder ──

  public static <T extends Region> T prepare(T region) {
    if (!region.getStyleClass().contains("jvn-fx-icon")) {
      region.getStyleClass().add("jvn-fx-icon");
    }
    region.setScaleShape(true);
    region.setCenterShape(true);
    region.setCacheShape(true);
    region.setSnapToPixel(true);
    region.setPickOnBounds(false);
    region.setMouseTransparent(true);
    return region;
  }

  public static Region icon(String svgPath, String color, double size) {
    Region r = prepare(new Region());
    r.setMinSize(size, size);
    r.setMaxSize(size, size);
    r.setPrefSize(size, size);
    r.setStyle(
        "-fx-shape: '" + svgPath + "';"
        + " -fx-background-color: " + color + ";"
        + " -fx-min-width: " + size + ";"
        + " -fx-min-height: " + size + ";"
        + " -fx-max-width: " + size + ";"
        + " -fx-max-height: " + size + ";"
    );
    return r;
  }

private static final String PATH_SKIP_PREVIOUS = "M7 6c.55 0 1 .45 1 1v10c0 .55-.45 1-1 1s-1-.45-1-1V7c0-.55.45-1 1-1zm3.66 6.82 5.77 4.07c.66.47 1.58-.01 1.58-.82V7.93c0-.81-.91-1.28-1.58-.82l-5.77 4.07a1 1 0 0 0 0 1.64z";
public static Region skipPrevious() { return skipPrevious("#b0b8c8"); }
public static Region skipPrevious(String color) { return icon(PATH_SKIP_PREVIOUS, color, 14); }

private static final String PATH_PAUSE = "M8 19c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2s-2 .9-2 2v10c0 1.1.9 2 2 2zm6-12v10c0 1.1.9 2 2 2s2-.9 2-2V7c0-1.1-.9-2-2-2s-2 .9-2 2z";
public static Region pause() { return pause("#b0b8c8"); }
public static Region pause(String color) { return icon(PATH_PAUSE, color, 14); }

private static final String PATH_LOOP = "M12 4V2.21c0-.45-.54-.67-.85-.35l-2.8 2.79c-.2.2-.2.51 0 .71l2.79 2.79c.32.31.86.09.86-.36V6c3.31 0 6 2.69 6 6 0 .79-.15 1.56-.44 2.25-.15.36-.04.77.23 1.04.51.51 1.37.33 1.64-.34.37-.91.57-1.91.57-2.95 0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-.79.15-1.56.44-2.25.15-.36.04-.77-.23-1.04-.51-.51-1.37-.33-1.64.34C4.2 9.96 4 10.96 4 12c0 4.42 3.58 8 8 8v1.79c0 .45.54.67.85.35l2.79-2.79c.2-.2.2-.51 0-.71l-2.79-2.79a.5.5 0 0 0-.85.36V18z";
public static Region loop() { return loop("#b0b8c8"); }
public static Region loop(String color) { return icon(PATH_LOOP, color, 14); }

private static final String PATH_VERTICAL_ALIGN_BOTTOM = "M14.79 13H13V4c0-.55-.45-1-1-1s-1 .45-1 1v9H9.21c-.45 0-.67.54-.35.85l2.79 2.79c.2.2.51.2.71 0l2.79-2.79a.5.5 0 0 0-.36-.85zM4 20c0 .55.45 1 1 1h14c.55 0 1-.45 1-1s-.45-1-1-1H5c-.55 0-1 .45-1 1z";
public static Region verticalAlignBottom() { return verticalAlignBottom("#b0b8c8"); }
public static Region verticalAlignBottom(String color) { return icon(PATH_VERTICAL_ALIGN_BOTTOM, color, 14); }

private static final String PATH_VERTICAL_ALIGN_TOP = "M9.21 11H11v9c0 .55.45 1 1 1s1-.45 1-1v-9h1.79c.45 0 .67-.54.35-.85l-2.79-2.79c-.2-.2-.51-.2-.71 0l-2.79 2.79a.5.5 0 0 0 .36.85zM4 4c0 .55.45 1 1 1h14c.55 0 1-.45 1-1s-.45-1-1-1H5c-.55 0-1 .45-1 1z";
public static Region verticalAlignTop() { return verticalAlignTop("#b0b8c8"); }
public static Region verticalAlignTop(String color) { return icon(PATH_VERTICAL_ALIGN_TOP, color, 14); }

private static final String PATH_CONTENT_PASTE = "M19 2h-4.18C14.4.84 13.3 0 12 0S9.6.84 9.18 2H5c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-7 0c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1zm6 18H6c-.55 0-1-.45-1-1V5c0-.55.45-1 1-1h1v1c0 1.1.9 2 2 2h6c1.1 0 2-.9 2-2V4h1c.55 0 1 .45 1 1v14c0 .55-.45 1-1 1z";
public static Region contentPaste() { return contentPaste("#b0b8c8"); }
public static Region contentPaste(String color) { return icon(PATH_CONTENT_PASTE, color, 14); }

private static final String PATH_CONTROL_POINT_DUPLICATE = "M15 8c-.55 0-1 .45-1 1v2h-2c-.55 0-1 .45-1 1s.45 1 1 1h2v2c0 .55.45 1 1 1s1-.45 1-1v-2h2c.55 0 1-.45 1-1s-.45-1-1-1h-2V9c0-.55-.45-1-1-1zM2 12c0-2.58 1.4-4.83 3.48-6.04.32-.19.53-.51.53-.88 0-.77-.84-1.25-1.51-.86C1.82 5.78 0 8.68 0 12s1.82 6.22 4.5 7.78c.67.39 1.51-.09 1.51-.86 0-.37-.21-.69-.53-.88A6.98 6.98 0 0 1 2 12zm13-9c-4.96 0-9 4.04-9 9s4.04 9 9 9 9-4.04 9-9-4.04-9-9-9zm0 16c-3.86 0-7-3.14-7-7s3.14-7 7-7 7 3.14 7 7-3.14 7-7 7z";
public static Region controlPointDuplicate() { return controlPointDuplicate("#b0b8c8"); }
public static Region controlPointDuplicate(String color) { return icon(PATH_CONTROL_POINT_DUPLICATE, color, 14); }

private static final String PATH_LIBRARY_ADD = "M3 6c-.55 0-1 .45-1 1v13c0 1.1.9 2 2 2h13c.55 0 1-.45 1-1s-.45-1-1-1H5c-.55 0-1-.45-1-1V7c0-.55-.45-1-1-1zm17-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-2 9h-3v3c0 .55-.45 1-1 1s-1-.45-1-1v-3h-3c-.55 0-1-.45-1-1s.45-1 1-1h3V6c0-.55.45-1 1-1s1 .45 1 1v3h3c.55 0 1 .45 1 1s-.45 1-1 1z";
public static Region libraryAdd() { return libraryAdd("#b0b8c8"); }
public static Region libraryAdd(String color) { return icon(PATH_LIBRARY_ADD, color, 14); }

private static final String PATH_INPUT = "M21 3.01H3c-1.1 0-2 .9-2 2V8c0 .55.45 1 1 1s1-.45 1-1V5.99c0-.55.45-1 1-1h16c.55 0 1 .45 1 1v12.03c0 .55-.45 1-1 1H4c-.55 0-1-.45-1-1V16c0-.55-.45-1-1-1s-1 .45-1 1v3.01c0 1.09.89 1.98 1.98 1.98H21c1.1 0 2-.9 2-2V5.01c0-1.1-.9-2-2-2zm-9.15 12.14 2.79-2.79c.2-.2.2-.51 0-.71l-2.79-2.79a.495.495 0 0 0-.85.35V11H2c-.55 0-1 .45-1 1s.45 1 1 1h9v1.79c0 .45.54.67.85.36z";
public static Region input() { return input("#b0b8c8"); }
public static Region input(String color) { return icon(PATH_INPUT, color, 14); }

private static final String PATH_EMOJI_PEOPLE = "M15.89 8.11C15.5 7.72 14.83 7 13.53 7h-2.54a5.023 5.023 0 0 1-4.92-4.15.998.998 0 0 0-.98-.85c-.61 0-1.09.54-1 1.14A7.037 7.037 0 0 0 9 8.71V21c0 .55.45 1 1 1s1-.45 1-1v-5h2v5c0 .55.45 1 1 1s1-.45 1-1V10.05l3.24 3.24a.996.996 0 1 0 1.41-1.41l-3.76-3.77z";
public static Region emojiPeople() { return emojiPeople("#b0b8c8"); }
public static Region emojiPeople(String color) { return icon(PATH_EMOJI_PEOPLE, color, 14); }

private static final String PATH_ZOOM_OUT_MAP = "M15.85 3.85 17.3 5.3l-2.18 2.16c-.39.39-.39 1.03 0 1.42.39.39 1.03.39 1.42 0L18.7 6.7l1.45 1.45a.5.5 0 0 0 .85-.36V3.5c0-.28-.22-.5-.5-.5h-4.29a.5.5 0 0 0-.36.85zm-12 4.3L5.3 6.7l2.16 2.18c.39.39 1.03.39 1.42 0 .39-.39.39-1.03 0-1.42L6.7 5.3l1.45-1.45A.5.5 0 0 0 7.79 3H3.5c-.28 0-.5.22-.5.5v4.29c0 .45.54.67.85.36zm4.3 12L6.7 18.7l2.18-2.16c.39-.39.39-1.03 0-1.42-.39-.39-1.03-.39-1.42 0L5.3 17.3l-1.45-1.45a.5.5 0 0 0-.85.36v4.29c0 .28.22.5.5.5h4.29a.5.5 0 0 0 .36-.85zm12-4.3L18.7 17.3l-2.16-2.18c-.39-.39-1.03-.39-1.42 0-.39.39-.39 1.03 0 1.42l2.18 2.16-1.45 1.45a.5.5 0 0 0 .36.85h4.29c.28 0 .5-.22.5-.5v-4.29a.5.5 0 0 0-.85-.36z";
public static Region zoomOutMap() { return zoomOutMap("#b0b8c8"); }
public static Region zoomOutMap(String color) { return icon(PATH_ZOOM_OUT_MAP, color, 14); }

private static final String PATH_MY_LOCATION = "M12 8c-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4-1.79-4-4-4zm8.94 3A8.994 8.994 0 0 0 13 3.06V2c0-.55-.45-1-1-1s-1 .45-1 1v1.06A8.994 8.994 0 0 0 3.06 11H2c-.55 0-1 .45-1 1s.45 1 1 1h1.06A8.994 8.994 0 0 0 11 20.94V22c0 .55.45 1 1 1s1-.45 1-1v-1.06A8.994 8.994 0 0 0 20.94 13H22c.55 0 1-.45 1-1s-.45-1-1-1h-1.06zM12 19c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z";
public static Region myLocation() { return myLocation("#b0b8c8"); }
public static Region myLocation(String color) { return icon(PATH_MY_LOCATION, color, 14); }

private static final String PATH_FAST_REWIND = "M11 16.07V7.93c0-.81-.91-1.28-1.58-.82l-5.77 4.07c-.56.4-.56 1.24 0 1.63l5.77 4.07c.67.47 1.58 0 1.58-.81zm1.66-3.25 5.77 4.07c.66.47 1.58-.01 1.58-.82V7.93c0-.81-.91-1.28-1.58-.82l-5.77 4.07a1 1 0 0 0 0 1.64z";
public static Region fastRewind() { return fastRewind("#b0b8c8"); }
public static Region fastRewind(String color) { return icon(PATH_FAST_REWIND, color, 14); }

private static final String PATH_FAST_FORWARD = "m5.58 16.89 5.77-4.07c.56-.4.56-1.24 0-1.63L5.58 7.11C4.91 6.65 4 7.12 4 7.93v8.14c0 .81.91 1.28 1.58.82zM13 7.93v8.14c0 .81.91 1.28 1.58.82l5.77-4.07c.56-.4.56-1.24 0-1.63l-5.77-4.07c-.67-.47-1.58 0-1.58.81z";
public static Region fastForward() { return fastForward("#b0b8c8"); }
public static Region fastForward(String color) { return icon(PATH_FAST_FORWARD, color, 14); }

private static final String PATH_WRAP_TEXT = "M5 7h14c.55 0 1-.45 1-1s-.45-1-1-1H5c-.55 0-1 .45-1 1s.45 1 1 1zm11.83 4H5c-.55 0-1 .45-1 1s.45 1 1 1h12.13c1 0 1.93.67 2.09 1.66.21 1.25-.76 2.34-1.97 2.34H15v-.79c0-.45-.54-.67-.85-.35l-1.79 1.79c-.2.2-.2.51 0 .71l1.79 1.79c.32.32.85.09.85-.35V19h2c2.34 0 4.21-2.01 3.98-4.39-.2-2.08-2.06-3.61-4.15-3.61zM9 17H5c-.55 0-1 .45-1 1s.45 1 1 1h4c.55 0 1-.45 1-1s-.45-1-1-1z";
public static Region wrapText() { return wrapText("#b0b8c8"); }
public static Region wrapText(String color) { return icon(PATH_WRAP_TEXT, color, 14); }

private static final String PATH_FORMAT_ALIGN_JUSTIFY = "M4 21h16c.55 0 1-.45 1-1s-.45-1-1-1H4c-.55 0-1 .45-1 1s.45 1 1 1zm0-4h16c.55 0 1-.45 1-1s-.45-1-1-1H4c-.55 0-1 .45-1 1s.45 1 1 1zm0-4h16c.55 0 1-.45 1-1s-.45-1-1-1H4c-.55 0-1 .45-1 1s.45 1 1 1zm0-4h16c.55 0 1-.45 1-1s-.45-1-1-1H4c-.55 0-1 .45-1 1s.45 1 1 1zM3 4c0 .55.45 1 1 1h16c.55 0 1-.45 1-1s-.45-1-1-1H4c-.55 0-1 .45-1 1z";
public static Region formatAlignJustify() { return formatAlignJustify("#b0b8c8"); }
public static Region formatAlignJustify(String color) { return icon(PATH_FORMAT_ALIGN_JUSTIFY, color, 14); }

private static final String PATH_SWAP_HORIZ = "m6.14 11.86-2.78 2.79c-.19.2-.19.51 0 .71l2.78 2.79c.31.32.85.09.85-.35V16H13c.55 0 1-.45 1-1s-.45-1-1-1H6.99v-1.79c0-.45-.54-.67-.85-.35zm14.51-3.21-2.78-2.79c-.31-.32-.85-.09-.85.35V8H11c-.55 0-1 .45-1 1s.45 1 1 1h6.01v1.79c0 .45.54.67.85.35l2.78-2.79c.2-.19.2-.51.01-.7z";
public static Region swapHoriz() { return swapHoriz("#b0b8c8"); }
public static Region swapHoriz(String color) { return icon(PATH_SWAP_HORIZ, color, 14); }

private static final String PATH_OPEN_IN_FULL = "M21 8.59V4c0-.55-.45-1-1-1h-4.59c-.89 0-1.34 1.08-.71 1.71l1.59 1.59-10 10-1.59-1.59c-.62-.63-1.7-.19-1.7.7V20c0 .55.45 1 1 1h4.59c.89 0 1.34-1.08.71-1.71L7.71 17.7l10-10 1.59 1.59c.62.63 1.7.19 1.7-.7z";
public static Region openInFull() { return openInFull("#b0b8c8"); }
public static Region openInFull(String color) { return icon(PATH_OPEN_IN_FULL, color, 14); }

private static final String PATH_CLOSE_FULLSCREEN = "M21.29 4.12 16.7 8.71l1.59 1.59c.63.63.18 1.71-.71 1.71H13c-.55 0-1-.45-1-1v-4.6c0-.89 1.08-1.34 1.71-.71l1.59 1.59 4.59-4.59a.996.996 0 0 1 1.41 0c.38.4.38 1.03-.01 1.42zM4.12 21.29l4.59-4.59 1.59 1.59c.63.63 1.71.18 1.71-.71V13c0-.55-.45-1-1-1h-4.6c-.89 0-1.34 1.08-.71 1.71l1.59 1.59-4.59 4.59a.996.996 0 0 0 0 1.41c.4.38 1.03.38 1.42-.01z";
public static Region closeFullscreen() { return closeFullscreen("#b0b8c8"); }
public static Region closeFullscreen(String color) { return icon(PATH_CLOSE_FULLSCREEN, color, 14); }

private static final String PATH_FOLDER_ZIP = "M20 6h-8l-1.41-1.41C10.21 4.21 9.7 4 9.17 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm-2 6h-2v2h2v2h-2v2h-2v-2h2v-2h-2v-2h2v-2h-2V8h2v2h2v2z";
public static Region folderZip() { return folderZip("#b0b8c8"); }
public static Region folderZip(String color) { return icon(PATH_FOLDER_ZIP, color, 14); }

private static final String PATH_GRID_4X4 = "M22 6c0-.55-.45-1-1-1h-2V3c0-.55-.45-1-1-1s-1 .45-1 1v2h-4V3c0-.55-.45-1-1-1s-1 .45-1 1v2H7V3c0-.55-.45-1-1-1s-1 .45-1 1v2H3c-.55 0-1 .45-1 1s.45 1 1 1h2v4H3c-.55 0-1 .45-1 1s.45 1 1 1h2v4H3c-.55 0-1 .45-1 1s.45 1 1 1h2v2c0 .55.45 1 1 1s1-.45 1-1v-2h4v2c0 .55.45 1 1 1s1-.45 1-1v-2h4v2c0 .55.45 1 1 1s1-.45 1-1v-2h2c.55 0 1-.45 1-1s-.45-1-1-1h-2v-4h2c.55 0 1-.45 1-1s-.45-1-1-1h-2V7h2c.55 0 1-.45 1-1zM7 7h4v4H7V7zm0 10v-4h4v4H7zm10 0h-4v-4h4v4zm0-6h-4V7h4v4z";
public static Region grid4x4() { return grid4x4("#b0b8c8"); }
public static Region grid4x4(String color) { return icon(PATH_GRID_4X4, color, 14); }

private static final String PATH_FIBER_SMART_RECORD = "M17 5.55v.18c0 .37.23.69.57.85C19.6 7.54 21 9.61 21 12s-1.4 4.46-3.43 5.42c-.34.16-.57.47-.57.84v.18c0 .68.71 1.11 1.32.82C21.08 18.01 23 15.23 23 12s-1.92-6.01-4.68-7.27c-.61-.28-1.32.14-1.32.82z";
public static Region fiberSmartRecord() { return fiberSmartRecord("#b0b8c8"); }
public static Region fiberSmartRecord(String color) { return icon(PATH_FIBER_SMART_RECORD, color, 14); }

private static final String PATH_BORDER_ALL = "M3 5v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2zm8 14H6c-.55 0-1-.45-1-1v-5h5c.55 0 1 .45 1 1v5zm-1-8H5V6c0-.55.45-1 1-1h5v5c0 .55-.45 1-1 1zm8 8h-5v-5c0-.55.45-1 1-1h5v5c0 .55-.45 1-1 1zm1-8h-5c-.55 0-1-.45-1-1V5h5c.55 0 1 .45 1 1v5z";
public static Region borderAll() { return borderAll("#b0b8c8"); }
public static Region borderAll(String color) { return icon(PATH_BORDER_ALL, color, 14); }

private static final String PATH_JOIN_INNER = "M12.68 6.8c-.39-.35-.98-.35-1.37 0C9.35 8.56 9 10.84 9 12c0 1.15.35 3.44 2.32 5.2.39.35.98.35 1.37 0C14.65 15.44 15 13.16 15 12c0-1.15-.35-3.44-2.32-5.2z M9.04 16.87c-.33.08-.68.13-1.04.13-2.76 0-5-2.24-5-5s2.24-5 5-5c.36 0 .71.05 1.04.13.39-.56.88-1.12 1.49-1.63C9.75 5.19 8.9 5 8 5c-3.86 0-7 3.14-7 7s3.14 7 7 7c.9 0 1.75-.19 2.53-.5-.61-.51-1.1-1.07-1.49-1.63zM16 5c-.9 0-1.75.19-2.53.5.61.51 1.1 1.07 1.49 1.63.33-.08.68-.13 1.04-.13 2.76 0 5 2.24 5 5s-2.24 5-5 5c-.36 0-.71-.05-1.04-.13-.39.56-.88 1.12-1.49 1.63.78.31 1.63.5 2.53.5 3.86 0 7-3.14 7-7s-3.14-7-7-7z";
public static Region joinInner() { return joinInner("#b0b8c8"); }
public static Region joinInner(String color) { return icon(PATH_JOIN_INNER, color, 14); }

private static final String PATH_THREE_SIXTY = "M12 7C6.48 7 2 9.24 2 12c0 2.24 2.94 4.13 7 4.77v2.02c0 .45.54.67.85.35l2.79-2.79c.2-.2.2-.51 0-.71l-2.79-2.79a.5.5 0 0 0-.85.36v1.52c-3.15-.56-5-1.9-5-2.73 0-1.06 3.04-3 8-3s8 1.94 8 3c0 .66-1.2 1.68-3.32 2.34-.41.13-.68.51-.68.94 0 .67.65 1.16 1.28.96C20.11 15.36 22 13.79 22 12c0-2.76-4.48-5-10-5z";
public static Region threeSixty() { return threeSixty("#b0b8c8"); }
public static Region threeSixty(String color) { return icon(PATH_THREE_SIXTY, color, 14); }

private static final String PATH_EXPLORE = "M12 10.9c-.61 0-1.1.49-1.1 1.1s.49 1.1 1.1 1.1c.61 0 1.1-.49 1.1-1.1s-.49-1.1-1.1-1.1zM12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm2.19 12.19L6 18l3.81-8.19L18 6l-3.81 8.19z";
public static Region explore() { return explore("#b0b8c8"); }
public static Region explore(String color) { return icon(PATH_EXPLORE, color, 14); }




private static final String PATH_NEAR_ME = "M18.75 3.94 4.07 10.08c-.83.35-.81 1.53.02 1.85L9.43 14a1 1 0 0 1 .57.57l2.06 5.33c.32.84 1.51.86 1.86.03l6.15-14.67c.33-.83-.5-1.66-1.32-1.32z";
public static Region nearMe() { return nearMe("#b0b8c8"); }
public static Region nearMe(String color) { return icon(PATH_NEAR_ME, color, 14); }

}
