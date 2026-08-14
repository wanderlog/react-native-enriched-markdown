#pragma once

#import "ENRMBlockRange.h"
#import "ENRMInputStyledRange.h"
#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

typedef struct {
  BOOL bold;
  BOOL italic;
  BOOL underline;
  BOOL strikethrough;
  BOOL spoiler;
  BOOL link;
  // For adding link destination to StyleState
  __unsafe_unretained NSString *linkDestination;
  NSInteger headingLevel;
  BOOL unorderedList;
  BOOL orderedList;
  NSInteger listDepth;
} ENRMInputStyleSnapshot;

@protocol ENRMInputStyleStateDataSource <NSObject>

- (NSRange)selectedRange;
- (BOOL)isEffectiveStyleActive:(ENRMInputStyleType)type atPosition:(NSUInteger)position;
- (BOOL)isStyleActive:(ENRMInputStyleType)type inRange:(NSRange)range;
- (NSInteger)headingLevelForCursorParagraph;
- (nullable ENRMBlockRange *)listBlockForCursorParagraph;
// For adding link destination to StyleState
- (NSString *)linkURLAtPosition:(NSUInteger)position;

@end

@interface ENRMInputStyleStateBuilder : NSObject

+ (ENRMInputStyleSnapshot)snapshotAtCurrentCursor:(id<ENRMInputStyleStateDataSource>)dataSource;

+ (ENRMInputStyleSnapshot)snapshotForRange:(NSRange)range dataSource:(id<ENRMInputStyleStateDataSource>)dataSource;

@end

NS_ASSUME_NONNULL_END
