/*
 * SIMJOT - MIT License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 *
 * See LICENSE.md for full terms.
 */

#ifdef __APPLE__

#import <Cocoa/Cocoa.h>
#import <QuartzCore/QuartzCore.h>
#include <atomic>
#include <mutex>
#include <functional>
#include <string>

// ═══════════════════════════════════════════════════════════════════════════
// MENU BAR SERVICE - Quick Entry Panel
// ═══════════════════════════════════════════════════════════════════════════

// Callback function type for when entry is submitted
typedef void (*QuickEntryCallback)(const char* text, int format_flags);

// Format flags
static const int FORMAT_BOLD      = 1 << 0;
static const int FORMAT_ITALIC    = 1 << 1;
static const int FORMAT_UNDERLINE = 1 << 2;
static const int FORMAT_BULLET    = 1 << 3;
static const int FORMAT_HEADER    = 1 << 4;

// Global state
static std::atomic<bool> g_menubar_initialized{false};
static std::mutex g_menubar_mutex;
static NSStatusItem* g_statusItem = nil;
static QuickEntryCallback g_submitCallback = nullptr;

// ═══════════════════════════════════════════════════════════════════════════
// FROSTED POPUP PANEL
// ═══════════════════════════════════════════════════════════════════════════

@interface SimjotQuickEntryPanel : NSPanel
@property (nonatomic, strong) NSVisualEffectView* blurView;
@property (nonatomic, strong) NSTextView* textView;
@property (nonatomic, strong) NSScrollView* scrollView;
@property (nonatomic, strong) NSStackView* formatBar;
@property (nonatomic, assign) int currentFormatFlags;
@property (nonatomic, strong) NSButton* boldBtn;
@property (nonatomic, strong) NSButton* italicBtn;
@property (nonatomic, strong) NSButton* underlineBtn;
@property (nonatomic, strong) NSButton* bulletBtn;
@property (nonatomic, strong) NSButton* headerBtn;
@end

@implementation SimjotQuickEntryPanel

- (instancetype)init {
    NSRect frame = NSMakeRect(0, 0, 360, 280);
    self = [super initWithContentRect:frame
                            styleMask:(NSWindowStyleMaskTitled | 
                                       NSWindowStyleMaskClosable |
                                       NSWindowStyleMaskNonactivatingPanel)
                              backing:NSBackingStoreBuffered
                                defer:NO];
    if (self) {
        [self setLevel:NSStatusWindowLevel];
        [self setCollectionBehavior:NSWindowCollectionBehaviorCanJoinAllSpaces |
                                    NSWindowCollectionBehaviorTransient];
        [self setHidesOnDeactivate:YES];
        [self setMovableByWindowBackground:YES];
        [self setOpaque:NO];
        [self setBackgroundColor:[NSColor clearColor]];
        [self setTitleVisibility:NSWindowTitleHidden];
        [self setTitlebarAppearsTransparent:YES];
        
        _currentFormatFlags = 0;
        
        [self setupUI];
    }
    return self;
}

- (void)setupUI {
    NSView* contentView = [self contentView];
    
    // Frosted glass background
    _blurView = [[NSVisualEffectView alloc] initWithFrame:contentView.bounds];
    _blurView.autoresizingMask = NSViewWidthSizable | NSViewHeightSizable;
    _blurView.blendingMode = NSVisualEffectBlendingModeBehindWindow;
    _blurView.material = NSVisualEffectMaterialHUDWindow;
    _blurView.state = NSVisualEffectStateActive;
    _blurView.wantsLayer = YES;
    _blurView.layer.cornerRadius = 12.0;
    _blurView.layer.masksToBounds = YES;
    [contentView addSubview:_blurView];
    
    // Container for content
    NSView* container = [[NSView alloc] initWithFrame:NSMakeRect(12, 12, 336, 256)];
    container.autoresizingMask = NSViewWidthSizable | NSViewHeightSizable;
    [_blurView addSubview:container];
    
    // Header label
    NSTextField* headerLabel = [NSTextField labelWithString:@"Quick Entry"];
    headerLabel.font = [NSFont boldSystemFontOfSize:14];
    headerLabel.textColor = [NSColor labelColor];
    headerLabel.frame = NSMakeRect(0, 232, 200, 20);
    [container addSubview:headerLabel];
    
    // Close button
    NSButton* closeBtn = [NSButton buttonWithImage:[NSImage imageWithSystemSymbolName:@"xmark.circle.fill" 
                                                              accessibilityDescription:@"Close"]
                                            target:self
                                            action:@selector(closePanel)];
    closeBtn.bordered = NO;
    closeBtn.frame = NSMakeRect(308, 228, 24, 24);
    closeBtn.contentTintColor = [NSColor secondaryLabelColor];
    [container addSubview:closeBtn];
    
    // Formatting toolbar
    _formatBar = [[NSStackView alloc] initWithFrame:NSMakeRect(0, 200, 336, 28)];
    _formatBar.orientation = NSUserInterfaceLayoutOrientationHorizontal;
    _formatBar.spacing = 4;
    _formatBar.distribution = NSStackViewDistributionFillEqually;
    [container addSubview:_formatBar];
    
    // Create toolbar buttons
    _boldBtn = [self createToolbarButton:@"bold" tooltip:@"Bold (⌘B)" action:@selector(toggleBold:)];
    _italicBtn = [self createToolbarButton:@"italic" tooltip:@"Italic (⌘I)" action:@selector(toggleItalic:)];
    _underlineBtn = [self createToolbarButton:@"underline" tooltip:@"Underline (⌘U)" action:@selector(toggleUnderline:)];
    _bulletBtn = [self createToolbarButton:@"list.bullet" tooltip:@"Bullet List" action:@selector(toggleBullet:)];
    _headerBtn = [self createToolbarButton:@"textformat.size.larger" tooltip:@"Header" action:@selector(toggleHeader:)];
    
    [_formatBar addArrangedSubview:_boldBtn];
    [_formatBar addArrangedSubview:_italicBtn];
    [_formatBar addArrangedSubview:_underlineBtn];
    [_formatBar addArrangedSubview:_bulletBtn];
    [_formatBar addArrangedSubview:_headerBtn];
    
    // Add separator
    NSBox* separator = [[NSBox alloc] initWithFrame:NSMakeRect(0, 195, 336, 1)];
    separator.boxType = NSBoxSeparator;
    [container addSubview:separator];
    
    // Text view with scroll
    _scrollView = [[NSScrollView alloc] initWithFrame:NSMakeRect(0, 44, 336, 148)];
    _scrollView.hasVerticalScroller = YES;
    _scrollView.hasHorizontalScroller = NO;
    _scrollView.autohidesScrollers = YES;
    _scrollView.borderType = NSNoBorder;
    _scrollView.drawsBackground = NO;
    
    _textView = [[NSTextView alloc] initWithFrame:NSMakeRect(0, 0, 336, 148)];
    _textView.minSize = NSMakeSize(0, 148);
    _textView.maxSize = NSMakeSize(FLT_MAX, FLT_MAX);
    _textView.verticallyResizable = YES;
    _textView.horizontallyResizable = NO;
    _textView.autoresizingMask = NSViewWidthSizable;
    _textView.textContainer.containerSize = NSMakeSize(336, FLT_MAX);
    _textView.textContainer.widthTracksTextView = YES;
    _textView.richText = YES;
    _textView.allowsUndo = YES;
    _textView.drawsBackground = NO;
    _textView.font = [NSFont systemFontOfSize:14];
    _textView.textColor = [NSColor labelColor];
    _textView.insertionPointColor = [NSColor labelColor];
    
    // Placeholder text
    NSMutableAttributedString* placeholder = [[NSMutableAttributedString alloc] 
        initWithString:@"Start typing your entry..."
        attributes:@{
            NSForegroundColorAttributeName: [NSColor placeholderTextColor],
            NSFontAttributeName: [NSFont systemFontOfSize:14]
        }];
    
    _scrollView.documentView = _textView;
    [container addSubview:_scrollView];
    
    // Bottom bar with submit button
    NSBox* bottomSep = [[NSBox alloc] initWithFrame:NSMakeRect(0, 40, 336, 1)];
    bottomSep.boxType = NSBoxSeparator;
    [container addSubview:bottomSep];
    
    // Character count label
    NSTextField* charCountLabel = [NSTextField labelWithString:@"0 characters"];
    charCountLabel.font = [NSFont systemFontOfSize:11];
    charCountLabel.textColor = [NSColor secondaryLabelColor];
    charCountLabel.frame = NSMakeRect(0, 8, 150, 20);
    charCountLabel.tag = 100; // For updating later
    [container addSubview:charCountLabel];
    
    // Submit button
    NSButton* submitBtn = [NSButton buttonWithTitle:@"Add Entry" 
                                             target:self 
                                             action:@selector(submitEntry)];
    submitBtn.bezelStyle = NSBezelStyleRounded;
    submitBtn.keyEquivalent = @"\r"; // Enter key
    submitBtn.frame = NSMakeRect(240, 4, 96, 32);
    [container addSubview:submitBtn];
    
    // Discard button
    NSButton* discardBtn = [NSButton buttonWithTitle:@"Discard" 
                                              target:self 
                                              action:@selector(discardEntry)];
    discardBtn.bezelStyle = NSBezelStyleRounded;
    discardBtn.frame = NSMakeRect(150, 4, 80, 32);
    [container addSubview:discardBtn];
    
    // Set up text change notification
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(textDidChange:)
                                                 name:NSTextDidChangeNotification
                                               object:_textView];
}

- (NSButton*)createToolbarButton:(NSString*)symbolName tooltip:(NSString*)tooltip action:(SEL)action {
    NSImage* image = [NSImage imageWithSystemSymbolName:symbolName accessibilityDescription:tooltip];
    NSButton* btn = [NSButton buttonWithImage:image target:self action:action];
    btn.bordered = NO;
    btn.bezelStyle = NSBezelStyleTexturedRounded;
    btn.toolTip = tooltip;
    [btn setButtonType:NSButtonTypeToggle];
    return btn;
}

- (void)closePanel {
    [self orderOut:nil];
}

- (void)toggleBold:(id)sender {
    _currentFormatFlags ^= FORMAT_BOLD;
    [self applyFormatToSelection:NSFontBoldTrait];
}

- (void)toggleItalic:(id)sender {
    _currentFormatFlags ^= FORMAT_ITALIC;
    [self applyFormatToSelection:NSFontItalicTrait];
}

- (void)toggleUnderline:(id)sender {
    _currentFormatFlags ^= FORMAT_UNDERLINE;
    NSRange range = [_textView selectedRange];
    if (range.length > 0) {
        NSMutableAttributedString* attrStr = [[NSMutableAttributedString alloc] 
            initWithAttributedString:[_textView.textStorage attributedSubstringFromRange:range]];
        
        id existingUnderline = [attrStr attribute:NSUnderlineStyleAttributeName atIndex:0 effectiveRange:nil];
        NSNumber* newUnderline = existingUnderline ? nil : @(NSUnderlineStyleSingle);
        
        [_textView.textStorage addAttribute:NSUnderlineStyleAttributeName 
                                      value:newUnderline ?: @0
                                      range:range];
    }
}

- (void)toggleBullet:(id)sender {
    _currentFormatFlags ^= FORMAT_BULLET;
    NSRange range = [_textView selectedRange];
    NSString* currentText = _textView.string;
    
    // Find line start
    NSUInteger lineStart = range.location;
    while (lineStart > 0 && [currentText characterAtIndex:lineStart - 1] != '\n') {
        lineStart--;
    }
    
    // Check if already has bullet
    if (lineStart < currentText.length && [currentText characterAtIndex:lineStart] == L'•') {
        // Remove bullet
        [_textView.textStorage replaceCharactersInRange:NSMakeRange(lineStart, 2) withString:@""];
    } else {
        // Add bullet
        [_textView.textStorage replaceCharactersInRange:NSMakeRange(lineStart, 0) withString:@"• "];
    }
}

- (void)toggleHeader:(id)sender {
    _currentFormatFlags ^= FORMAT_HEADER;
    NSRange range = [_textView selectedRange];
    if (range.length > 0) {
        NSFont* currentFont = [_textView.textStorage attribute:NSFontAttributeName atIndex:range.location effectiveRange:nil];
        CGFloat newSize = (currentFont.pointSize >= 18) ? 14 : 18;
        NSFont* newFont = [NSFont boldSystemFontOfSize:newSize];
        [_textView.textStorage addAttribute:NSFontAttributeName value:newFont range:range];
    }
}

- (void)applyFormatToSelection:(NSFontSymbolicTraits)trait {
    NSRange range = [_textView selectedRange];
    if (range.length == 0) return;
    
    NSFont* currentFont = [_textView.textStorage attribute:NSFontAttributeName atIndex:range.location effectiveRange:nil];
    if (!currentFont) currentFont = [NSFont systemFontOfSize:14];
    
    NSFontManager* fontManager = [NSFontManager sharedFontManager];
    NSFont* newFont;
    
    NSFontSymbolicTraits currentTraits = [[currentFont fontDescriptor] symbolicTraits];
    if (currentTraits & trait) {
        // Remove trait
        newFont = [fontManager convertFont:currentFont toNotHaveTrait:trait];
    } else {
        // Add trait
        newFont = [fontManager convertFont:currentFont toHaveTrait:trait];
    }
    
    if (newFont) {
        [_textView.textStorage addAttribute:NSFontAttributeName value:newFont range:range];
    }
}

- (void)textDidChange:(NSNotification*)notification {
    NSTextField* charCount = (NSTextField*)[[[self contentView] subviews][0] viewWithTag:100];
    if (charCount) {
        NSUInteger len = _textView.string.length;
        charCount.stringValue = [NSString stringWithFormat:@"%lu character%@", 
                                 (unsigned long)len, len == 1 ? @"" : @"s"];
    }
}

- (void)submitEntry {
    NSString* text = _textView.string;
    if (text.length == 0) {
        // Shake animation for empty text
        [self shakeWindow];
        return;
    }
    
    if (g_submitCallback) {
        g_submitCallback([text UTF8String], _currentFormatFlags);
    }
    
    // Clear and close
    [_textView setString:@""];
    _currentFormatFlags = 0;
    [self orderOut:nil];
    
    // No notification - just close silently
}

- (void)discardEntry {
    [_textView setString:@""];
    _currentFormatFlags = 0;
    [self orderOut:nil];
}

- (void)shakeWindow {
    CGFloat shakeDuration = 0.05;
    CGFloat shakeAmount = 8.0;
    
    NSRect frame = self.frame;
    
    [NSAnimationContext runAnimationGroup:^(NSAnimationContext* context) {
        context.duration = shakeDuration;
        [[self animator] setFrame:NSOffsetRect(frame, shakeAmount, 0) display:YES];
    } completionHandler:^{
        [NSAnimationContext runAnimationGroup:^(NSAnimationContext* context) {
            context.duration = shakeDuration;
            [[self animator] setFrame:NSOffsetRect(frame, -shakeAmount * 2, 0) display:YES];
        } completionHandler:^{
            [NSAnimationContext runAnimationGroup:^(NSAnimationContext* context) {
                context.duration = shakeDuration;
                [[self animator] setFrame:frame display:YES];
            }];
        }];
    }];
}

- (void)showAtStatusItemRect:(NSRect)rect {
    // Position below status item
    CGFloat x = NSMidX(rect) - (self.frame.size.width / 2);
    CGFloat y = NSMinY(rect) - self.frame.size.height - 4;
    
    // Ensure on screen
    NSScreen* screen = [NSScreen mainScreen];
    if (screen) {
        NSRect screenFrame = screen.visibleFrame;
        if (x < screenFrame.origin.x) x = screenFrame.origin.x + 8;
        if (x + self.frame.size.width > NSMaxX(screenFrame)) {
            x = NSMaxX(screenFrame) - self.frame.size.width - 8;
        }
        if (y < screenFrame.origin.y) {
            y = NSMaxY(rect) + 4; // Show above if no room below
        }
    }
    
    [self setFrameOrigin:NSMakePoint(x, y)];
    [self makeKeyAndOrderFront:nil];
    [_textView.window makeFirstResponder:_textView];
}

- (BOOL)canBecomeKeyWindow {
    return YES;
}

- (void)dealloc {
    [[NSNotificationCenter defaultCenter] removeObserver:self];
}

@end

// ═══════════════════════════════════════════════════════════════════════════
// STATUS ITEM CONTROLLER
// ═══════════════════════════════════════════════════════════════════════════

static SimjotQuickEntryPanel* g_quickEntryPanel = nil;

@interface SimjotStatusItemController : NSObject
@property (nonatomic, strong) NSStatusItem* statusItem;
@property (nonatomic, strong) SimjotQuickEntryPanel* panel;
@end

@implementation SimjotStatusItemController

- (instancetype)init {
    self = [super init];
    if (self) {
        [self setupStatusItem];
    }
    return self;
}

- (void)setupStatusItem {
    NSLog(@"[MENUBAR] setupStatusItem called");
    
    // Use fixed length for guaranteed visibility
    _statusItem = [[NSStatusBar systemStatusBar] statusItemWithLength:28.0];
    NSLog(@"[MENUBAR] statusItem created: %@", _statusItem);
    
    if (!_statusItem || !_statusItem.button) {
        NSLog(@"[MENUBAR] ERROR: Failed to create status item or button!");
        return;
    }
    
    // Use a simple emoji as the most reliable cross-platform icon
    _statusItem.button.title = @"📔";
    _statusItem.button.font = [NSFont systemFontOfSize:14];
    
    _statusItem.button.toolTip = @"Simjot Quick Entry";
    _statusItem.button.target = self;
    _statusItem.button.action = @selector(statusItemClicked:);
    
    // Make sure it's visible
    _statusItem.visible = YES;
    
    // Add a menu as fallback (sometimes required for visibility)
    NSMenu* menu = [[NSMenu alloc] init];
    [menu addItemWithTitle:@"Open Simjot" action:@selector(statusItemClicked:) keyEquivalent:@""];
    [menu addItem:[NSMenuItem separatorItem]];
    [menu addItemWithTitle:@"Quick Entry..." action:@selector(statusItemClicked:) keyEquivalent:@"n"];
    menu.itemArray[0].target = self;
    menu.itemArray[2].target = self;
    _statusItem.menu = menu;
    
    NSLog(@"[MENUBAR] statusItem button: %@", _statusItem.button);
    NSLog(@"[MENUBAR] statusItem menu: %@", _statusItem.menu);
    NSLog(@"[MENUBAR] statusItem visible: %d", _statusItem.visible);
    NSLog(@"[MENUBAR] statusItem length: %f", _statusItem.length);
    
    // Create the panel
    _panel = [[SimjotQuickEntryPanel alloc] init];
    g_quickEntryPanel = _panel;
    NSLog(@"[MENUBAR] panel created");
}

- (void)statusItemClicked:(id)sender {
    if (_panel.isVisible) {
        [_panel orderOut:nil];
    } else {
        NSRect rect = _statusItem.button.window.frame;
        [_panel showAtStatusItemRect:rect];
    }
}

- (void)showPanel {
    if (!_panel.isVisible) {
        NSRect rect = _statusItem.button.window.frame;
        [_panel showAtStatusItemRect:rect];
    }
}

- (void)hidePanel {
    [_panel orderOut:nil];
}

- (void)setIcon:(NSString*)symbolName {
    NSImage* icon = [NSImage imageWithSystemSymbolName:symbolName 
                                accessibilityDescription:@"Simjot"];
    if (icon) {
        [icon setTemplate:YES];
        _statusItem.button.image = icon;
    }
}

@end

static SimjotStatusItemController* g_statusController = nil;

// ═══════════════════════════════════════════════════════════════════════════
// C API
// ═══════════════════════════════════════════════════════════════════════════

extern "C" {

int32_t simjot_menubar_init(void) {
    std::lock_guard<std::mutex> lock(g_menubar_mutex);
    
    if (g_menubar_initialized.load()) {
        return 1; // Already initialized
    }
    
    // Mark as initialized early to prevent re-entry
    g_menubar_initialized.store(true);
    
    // Delay creation to ensure Java UI is fully initialized
    // This is necessary because Java AWT has its own event loop
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(2.0 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
        @autoreleasepool {
            @try {
                NSLog(@"[MENUBAR] Delayed init starting...");
                
                // Force NSApplication to exist - critical for JVM apps
                NSApplication* app = [NSApplication sharedApplication];
                NSLog(@"[MENUBAR] NSApplication: %@", app);
                
                g_statusController = [[SimjotStatusItemController alloc] init];
                g_statusItem = g_statusController.statusItem;
                
                NSLog(@"[MENUBAR] g_statusController retained: %@", g_statusController);
                NSLog(@"[MENUBAR] g_statusItem: %@", g_statusItem);
                
                // Force UI update - needed for Java AWT integration
                [[NSRunLoop currentRunLoop] runMode:NSDefaultRunLoopMode beforeDate:[NSDate dateWithTimeIntervalSinceNow:0.1]];
                [app updateWindows];
                
                // Also try to force status bar to redraw
                if (g_statusItem.button) {
                    [g_statusItem.button setNeedsDisplay:YES];
                    [g_statusItem.button displayIfNeeded];
                }
                
                NSLog(@"[MENUBAR] Delayed init complete, forced UI update");
            } @catch (NSException* e) {
                NSLog(@"Simjot menubar delayed init failed: %@", e);
                g_menubar_initialized.store(false);
            }
        }
    });
    
    return 1;
}

void simjot_menubar_shutdown(void) {
    std::lock_guard<std::mutex> lock(g_menubar_mutex);
    
    if (!g_menubar_initialized.load()) return;
    
    void (^shutdownBlock)(void) = ^{
        @autoreleasepool {
            if (g_quickEntryPanel) {
                [g_quickEntryPanel close];
                g_quickEntryPanel = nil;
            }
            if (g_statusItem) {
                [[NSStatusBar systemStatusBar] removeStatusItem:g_statusItem];
                g_statusItem = nil;
            }
            g_statusController = nil;
        }
    };
    
    if ([NSThread isMainThread]) {
        shutdownBlock();
    } else {
        dispatch_sync(dispatch_get_main_queue(), shutdownBlock);
    }
    
    g_menubar_initialized.store(false);
}

int32_t simjot_menubar_is_initialized(void) {
    return g_menubar_initialized.load() ? 1 : 0;
}

void simjot_menubar_show_panel(void) {
    if (!g_menubar_initialized.load()) return;
    
    dispatch_async(dispatch_get_main_queue(), ^{
        @autoreleasepool {
            [g_statusController showPanel];
        }
    });
}

void simjot_menubar_hide_panel(void) {
    if (!g_menubar_initialized.load()) return;
    
    dispatch_async(dispatch_get_main_queue(), ^{
        @autoreleasepool {
            [g_statusController hidePanel];
        }
    });
}

int32_t simjot_menubar_is_panel_visible(void) {
    if (!g_menubar_initialized.load()) return 0;
    
    __block int32_t visible = 0;
    
    if ([NSThread isMainThread]) {
        @autoreleasepool {
            visible = (g_quickEntryPanel && g_quickEntryPanel.isVisible) ? 1 : 0;
        }
    } else {
        dispatch_sync(dispatch_get_main_queue(), ^{
            @autoreleasepool {
                visible = (g_quickEntryPanel && g_quickEntryPanel.isVisible) ? 1 : 0;
            }
        });
    }
    return visible;
}

void simjot_menubar_set_callback(QuickEntryCallback callback) {
    std::lock_guard<std::mutex> lock(g_menubar_mutex);
    g_submitCallback = callback;
}

void simjot_menubar_set_icon(const char* symbol_name) {
    if (!g_menubar_initialized.load() || !symbol_name) return;
    
    NSString* symbolStr = [NSString stringWithUTF8String:symbol_name];
    dispatch_async(dispatch_get_main_queue(), ^{
        @autoreleasepool {
            [g_statusController setIcon:symbolStr];
        }
    });
}

void simjot_menubar_set_tooltip(const char* tooltip) {
    if (!g_menubar_initialized.load() || !tooltip) return;
    
    NSString* tooltipStr = [NSString stringWithUTF8String:tooltip];
    dispatch_async(dispatch_get_main_queue(), ^{
        @autoreleasepool {
            g_statusItem.button.toolTip = tooltipStr;
        }
    });
}

void simjot_menubar_set_badge(int32_t count) {
    if (!g_menubar_initialized.load()) return;
    
    dispatch_async(dispatch_get_main_queue(), ^{
        @autoreleasepool {
            if (count > 0) {
                // Show badge as overlay on icon
                NSString* badgeText = count > 99 ? @"99+" : [NSString stringWithFormat:@"%d", count];
                g_statusItem.button.title = badgeText;
            } else {
                g_statusItem.button.title = @"";
            }
        }
    });
}

int32_t simjot_menubar_get_panel_text(char* buffer, int32_t buffer_size) {
    if (!g_menubar_initialized.load() || !buffer || buffer_size <= 0) return 0;
    
    __block int32_t length = 0;
    
    void (^getTextBlock)(void) = ^{
        @autoreleasepool {
            if (g_quickEntryPanel) {
                NSString* text = g_quickEntryPanel.textView.string;
                if (text) {
                    const char* utf8 = [text UTF8String];
                    size_t len = strlen(utf8);
                    if (len < (size_t)buffer_size) {
                        strcpy(buffer, utf8);
                        length = (int32_t)len;
                    } else {
                        strncpy(buffer, utf8, buffer_size - 1);
                        buffer[buffer_size - 1] = '\0';
                        length = buffer_size - 1;
                    }
                }
            }
        }
    };
    
    if ([NSThread isMainThread]) {
        getTextBlock();
    } else {
        dispatch_sync(dispatch_get_main_queue(), getTextBlock);
    }
    return length;
}

void simjot_menubar_set_panel_text(const char* text) {
    if (!g_menubar_initialized.load() || !text) return;
    
    NSString* textStr = [NSString stringWithUTF8String:text];
    dispatch_async(dispatch_get_main_queue(), ^{
        @autoreleasepool {
            if (g_quickEntryPanel) {
                [g_quickEntryPanel.textView setString:textStr];
            }
        }
    });
}

void simjot_menubar_clear_panel(void) {
    if (!g_menubar_initialized.load()) return;
    
    dispatch_async(dispatch_get_main_queue(), ^{
        @autoreleasepool {
            if (g_quickEntryPanel) {
                [g_quickEntryPanel.textView setString:@""];
                g_quickEntryPanel.currentFormatFlags = 0;
            }
        }
    });
}

} // extern "C"

#endif // __APPLE__
