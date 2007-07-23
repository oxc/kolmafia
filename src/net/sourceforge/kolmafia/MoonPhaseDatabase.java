/**
 * Copyright (c) 2005-2007, KoLmafia development team
 * http://kolmafia.sourceforge.net/
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  [1] Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *  [2] Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in
 *      the documentation and/or other materials provided with the
 *      distribution.
 *  [3] Neither the name "KoLmafia" nor the names of its contributors may
 *      be used to endorse or promote products derived from this software
 *      without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package net.sourceforge.kolmafia;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class MoonPhaseDatabase extends StaticEntity
{
	private static long NEWYEAR = 0;
	private static long BOUNDARY = 0;
	private static long COLLISION = 0;

	static
	{
		try
		{
			// Change it so that it doesn't recognize daylight savings in order
			// to ensure different localizations work.

			Calendar myCalendar = Calendar.getInstance( TimeZone.getTimeZone( "GMT-5" ) );

			myCalendar.set( 2005, 8, 17, 0, 0, 0 );
			NEWYEAR = myCalendar.getTimeInMillis();

			myCalendar.set( 2005, 9, 27, 0, 0, 0 );
			BOUNDARY = myCalendar.getTimeInMillis();

			myCalendar.set( 2006, 5, 3, 0, 0, 0 );
			COLLISION = myCalendar.getTimeInMillis();
		}
		catch ( Exception e )
		{
			// This should not happen.  Therefore, print
			// a stack trace for debug purposes.

			printStackTrace( e );
		}
	}

	private static int RONALD_PHASE = -1;
	private static int GRIMACE_PHASE = -1;
	private static int HAMBURGLAR_POSITION = -1;

	static
	{
		try
		{
			int calendarDay = getCalendarDay( new Date() );
			int phaseStep = ((calendarDay % 16) + 16) % 16;

			RONALD_PHASE = phaseStep % 8;
			GRIMACE_PHASE = phaseStep / 2;
			HAMBURGLAR_POSITION = getHamburglarPosition( new Date() );
		}
		catch ( Exception e )
		{
			// This should not happen.  Therefore, print
			// a stack trace for debug purposes.

			printStackTrace( e );
		}
	}

	// Static array of status effect day predictions
	// within the KoL lunar calendar.

	private static final String [] STAT_EFFECT =
	{
		"Moxie bonus today and yesterday.", "3 days until Mysticism.", "2 days until Mysticism.", "Mysticism bonus tomorrow (not today).",
		"Mysticism bonus today (not tomorrow).", "3 days until Muscle.", "2 days until Muscle.", "Muscle bonus tomorrow (not today).",
		"Muscle bonus today and tomorrow.", "Muscle bonus today and yesterday.", "2 days until Mysticism.", "Mysticism bonus tomorrow (not today).",
		"Mysticism bonus today (not tomorrow).", "2 days until Moxie.", "Moxie bonus tomorrow (not today).", "Moxie bonus today and tomorrow."
	};

	// Static array of month names, as they exist within
	// the KoL calendar.

	private static final String [] MONTH_NAMES =
	{
		"", "Jarlsuary", "Frankruary", "Starch", "April", "Martinus", "Bill",
		"Bor", "Petember", "Carlvember", "Porktober", "Boozember", "Dougtember"
	};

	// Static array of HOLIDAYS.  This holiday is filled with the
	// name of the holiday which occurs on the given KoL month and
	// given KoL day.

	private static String [][] HOLIDAYS = new String[13][9];

	static
	{
		for ( int i = 0; i < 13; ++i )
			for ( int j = 0; j < 9; ++j )
				HOLIDAYS[i][j] = null;

		// Initialize all the known HOLIDAYS here so that
		// they can be used in later initializers.

		HOLIDAYS[2][4] = "Valentine's Day";
		HOLIDAYS[3][3] = "St. Sneaky Pete's Day";
		HOLIDAYS[4][2] = "Oyster Egg Day";
		HOLIDAYS[7][4] = "Dependence Day";
		HOLIDAYS[10][8] = "Halloween";
		HOLIDAYS[11][7] = "Feast of Boris";
	}

	// Static array of when the special events in KoL occur, including
	// stat days, HOLIDAYS and all that jazz.  Values are false where
	// there is no special occasion, and true where there is.

	private static int [] SPECIAL = new int[96];

	public static final int SP_NOTHING = 0;
	public static final int SP_HOLIDAY = 1;

	public static int SP_MUSDAY = 2;
	public static int SP_MYSDAY = 3;
	public static int SP_MOXDAY = 4;

	static
	{
		// Assume there are no special days at all, and then
		// fill them in once they're encountered.

		for ( int i = 0; i < 96; ++i )
			SPECIAL[i] = SP_NOTHING;

		// Muscle days occur every phase 8 and phase 9 on the
		// KoL calendar.

		for ( int i = 8; i < 96; i += 16 )
			SPECIAL[i] = SP_MUSDAY;
		for ( int i = 9; i < 96; i += 16 )
			SPECIAL[i] = SP_MUSDAY;

		// Mysticism days occur every phase 4 and phase 12 on the
		// KoL calendar.

		for ( int i = 4; i < 96; i += 16 )
			SPECIAL[i] = SP_MYSDAY;
		for ( int i = 12; i < 96; i += 16 )
			SPECIAL[i] = SP_MYSDAY;

		// Moxie days occur every phase 0 and phase 15 on the
		// KoL calendar.

		for ( int i = 0; i < 96; i += 16 )
			SPECIAL[i] = SP_MOXDAY;
		for ( int i = 15; i < 96; i += 16 )
			SPECIAL[i] = SP_MOXDAY;

		// Next, fill in the HOLIDAYS.  These are manually
		// computed based on the recurring day in the year
		// at which these occur.

		for ( int i = 0; i < 13; ++i )
			for ( int j = 0; j < 9; ++j )
				if ( HOLIDAYS[i][j] != null )
					SPECIAL[ 8 * i + j - 9 ] = SP_HOLIDAY;
	}

	public static final void setMoonPhases( int ronaldPhase, int grimacePhase )
	{
		// Reset the new year based on the internal
		// phase error.

		int phaseError = getPhaseStep();

		RONALD_PHASE = ronaldPhase;
		GRIMACE_PHASE = grimacePhase;

		phaseError -= getPhaseStep();

		// Adjust the new year by the appropriate
		// number of days.

		NEWYEAR += (phaseError) * 86400000L;
		BOUNDARY += (phaseError) * 86400000L;
		COLLISION += (phaseError) * 86400000L;
	}

	public static final int getRonaldPhase()
	{	return RONALD_PHASE + 1;
	}

	public static final int getGrimacePhase()
	{	return GRIMACE_PHASE + 1;
	}

	public static final int getHamburglarPosition( Date time )
	{
		long timeDifference = time.getTime();
		if ( timeDifference < COLLISION )
			return -1;

		timeDifference -= COLLISION;
		int dayDifference = (int) Math.floor( timeDifference / 86400000L );
		return (((dayDifference * 2) % 11) + 11) % 11;
	}

	/**
	 * Method to return which phase of the moon is currently
	 * appearing over the Kingdom of Loathing, as a string.
	 *
	 * @return	The current phase of Ronald
	 */

	public static final String getRonaldPhaseAsString()
	{	return getPhaseName( RONALD_PHASE );
	}

	/**
	 * Method to return which phase of the moon is currently
	 * appearing over the Kingdom of Loathing, as a string.
	 *
	 * @return	The current phase of Ronald
	 */

	public static final String getGrimacePhaseAsString()
	{	return getPhaseName( GRIMACE_PHASE );
	}

	public static final String getPhaseName( int phase )
	{
		switch ( phase )
		{
		case 0:  return "new moon";
		case 1:  return "waxing crescent";
		case 2:  return "first quarter";
		case 3:  return "waxing gibbous";
		case 4:  return "full moon";
		case 5:  return "waning gibbous";
		case 6:  return "third quarter";
		case 7:  return "waning crescent";
		default:  return "unknown";
		}
	}

	/**
	 * Returns the moon effect applicable today, or the amount
	 * of time until the next moon effect becomes applicable
	 * if today is not a moon effect day.
	 */

	public static final String getMoonEffect()
	{	return getMoonEffect( RONALD_PHASE, GRIMACE_PHASE );
	}

	/**
	 * Returns the moon effect applicable at the given phase
	 * step, or the amount of time until the next moon effect,
	 * given the phase value.
	 */

	public static final String getMoonEffect( int ronaldPhase, int grimacePhase )
	{
		int phaseStep = getPhaseStep( ronaldPhase, grimacePhase );
		return phaseStep == -1 ? "Could not determine moon phase." : STAT_EFFECT[ phaseStep ];
	}

	public static final int getRonaldMoonlight( int ronaldPhase )
	{	return ronaldPhase > 4 ? 8 - ronaldPhase : ronaldPhase;
	}

	public static final int getGrimaceMoonlight( int grimacePhase )
	{	return grimacePhase > 4 ? 8 - grimacePhase : grimacePhase;
	}

	/**
	 * Returns the "phase step" currently recognized by the
	 * KoL calendar.  This corresponds to the day within the
	 * KoL lunar calendar, which has a cycle of 16 days.
	 */

	public static final int getPhaseStep()
	{	return getPhaseStep( RONALD_PHASE, GRIMACE_PHASE );
	}

	/**
	 * Returns the "phase step" currently recognized by the
	 * KoL calendar, corresponding to the given phases.  This
	 * corresponds to the day within the KoL lunar calendar,
	 * which has a cycle of 16 days.
	 */

	public static final int getPhaseStep( int ronaldPhase, int grimacePhase )
	{	return grimacePhase >= 4 ? 8 + ronaldPhase : ronaldPhase;
	}

	/**
	 * Returns whether or not the grue will fight during the
	 * current moon phase.
	 */

	public static final boolean getGrueEffect()
	{	return getGrueEffect( RONALD_PHASE, GRIMACE_PHASE, HAMBURGLAR_POSITION );
	}

	/**
	 * Returns whether or not the grue will fight during the
	 * given moon phases.
	 */

	public static final boolean getGrueEffect( int ronaldPhase, int grimacePhase, int hamburglarPosition )
	{	return getMoonlight( ronaldPhase, grimacePhase, hamburglarPosition ) < 5;
	}

	/**
	 * Returns the effect percentage (as a whole number integer)
	 * of Blood of the Wereseal for today.
	 */

	public static final int getBloodEffect()
	{	return getBloodEffect( RONALD_PHASE, GRIMACE_PHASE, HAMBURGLAR_POSITION );
	}

	/**
	 * Returns the effect percentage (as a whole number integer)
	 * of Blood of the Wereseal for the given moon phase.
	 */

	public static final int getBloodEffect( int ronaldPhase, int grimacePhase, int hamburglarPosition )
	{	return 50 + (getMoonlight( ronaldPhase, grimacePhase, hamburglarPosition ) - 4) * 5;
	}

	/**
	 * Returns the effect percentage (as a whole number integer)
	 * of the Talisman of Baio for today.
	 */

	public static final int getBaioEffect()
	{	return getBaioEffect( RONALD_PHASE, GRIMACE_PHASE, HAMBURGLAR_POSITION );
	}

	/**
	 * Returns the effect percentage (as a whole number integer)
	 * of the Talisman of Baio for the given moon phases.
	 */

	public static final int getBaioEffect( int ronaldPhase, int grimacePhase, int hamburglarPosition )
	{	return getMoonlight( ronaldPhase, grimacePhase, hamburglarPosition ) * 10;
	}

	public static final int getGrimaciteEffect()
	{	return getGrimaciteEffect( RONALD_PHASE, GRIMACE_PHASE, HAMBURGLAR_POSITION );
	}

	public static final int getGrimaciteEffect( int ronaldPhase, int grimacePhase, int hamburglarPosition )
	{
		int grimaceEffect = 4 - getGrimaceMoonlight( grimacePhase );
		if ( getHamburglarLight( ronaldPhase, grimacePhase, hamburglarPosition ) != 1 )
			++grimaceEffect;

		return grimaceEffect * 10;
	}

	/**
	 * Returns the effect of the Jekyllin, based on the current
	 * moon phase information.
	 */

	public static final String getJekyllinEffect()
	{	return getJekyllinEffect( RONALD_PHASE, GRIMACE_PHASE, HAMBURGLAR_POSITION );
	}

	/**
	 * Returns the effect of the Jekyllin for the given moon phases
	 */

	public static final String getJekyllinEffect( int ronaldPhase, int grimacePhase, int hamburglarPosition )
	{
		int moonlight = getMoonlight( ronaldPhase, grimacePhase, hamburglarPosition );
		return "+" + (9 - moonlight) + " stats, " + (15 + moonlight * 5) + "% items";
	}

	/**
	 * Utility method which determines the moonlight available,
	 * given the current moon phases.
	 */

	public static final int getMoonlight()
	{	return getMoonlight( RONALD_PHASE, GRIMACE_PHASE, HAMBURGLAR_POSITION );
	}

	/**
	 * Utility method which determines the moonlight available,
	 * given the moon phases as stated.
	 */

	private static final int getMoonlight( int ronaldPhase, int grimacePhase, int hamburglarPosition )
	{
		int ronaldLight = getRonaldMoonlight( ronaldPhase );
		int grimaceLight = getGrimaceMoonlight( grimacePhase );
		int hamburglarLight = getHamburglarLight( ronaldPhase, grimacePhase, hamburglarPosition );
		return ronaldLight + grimaceLight + hamburglarLight;
	}

	public static int getHamburglarLight( int ronaldPhase, int grimacePhase, int hamburglarPosition )
	{
		//         6    5    4    3
		//
		//       /---\          /---\
		//   7   | R |          | G |   2
		//       \___/          \___/
		//
		//       8   9    10    0   1

		switch ( hamburglarPosition )
		{

		case 0:
			if ( grimacePhase > 0 && grimacePhase < 5 )
				return -1;
			return 1;

		case 1:
			if ( grimacePhase < 4 )
				return 1;
			return -1;

		case 2:
			if ( grimacePhase > 3 )
				return 1;
			return 0;

		case 4:
			if ( grimacePhase > 0 && grimacePhase < 5 )
				return 1;
			return 0;

		case 5:
			if ( ronaldPhase > 3 )
				return 1;
			return 0;

		case 7:
			if ( ronaldPhase > 0 && ronaldPhase < 5 )
				return 1;
			return 0;

		case 8:
			if ( ronaldPhase > 0 && ronaldPhase < 5 )
				return -1;
			return 1;

		case 9:
			if ( ronaldPhase < 4 )
				return 1;
			return -1;

		case 10:
			int totalEffect = 0;
			if ( ronaldPhase > 3 )
				++totalEffect;
			if ( grimacePhase > 0 && grimacePhase < 5 )
				++totalEffect;
			return totalEffect;

		default:
			return 0;

		}
	}

	/**
	 * Computes the difference in days based on the given
	 * millisecond counts since January 1, 1970.
	 */

	public static int getCalendarDay( Date time )
	{
		try
		{
			long timeDifference = DATED_FILENAME_FORMAT.parse( DATED_FILENAME_FORMAT.format( time ) ).getTime() - NEWYEAR;

			if ( timeDifference > BOUNDARY )
				timeDifference -= 86400000L;

			int dayDifference = (int) Math.floor( timeDifference / 86400000L );
			return ((dayDifference % 96) + 96) % 96;
		}
		catch ( Exception e )
		{
			StaticEntity.printStackTrace( e );
			return 0;
		}
	}

	/**
	 * Utility method which calculates which day of the
	 * KoL calendar you're currently on, based on the number
	 * of milliseconds since January 1, 1970.
	 */

	public static final String getCalendarDayAsString( Date time )
	{
		int [] calendarDayAsArray = convertCalendarDayToArray( getCalendarDay( time ) );
		return MONTH_NAMES[ calendarDayAsArray[0] ] + " " + calendarDayAsArray[1];
	}

	/**
	 * Utility method which decomposes a given calendar day
	 * into its actual calendar components.
	 */

	private static final int [] convertCalendarDayToArray( int calendarDay )
	{	return new int [] { ((calendarDay / 8) % 12) + 1, (calendarDay % 8) + 1 };
	}

	/**
	 * Utility method which returns the given day count as
	 * an easily-understood string (today, tomorrow) instead
	 * of just "x days".
	 */

	public static final String getDayCountAsString( int dayCount )
	{	return dayCount == 0 ? "today" : dayCount == 1 ? "tomorrow" : dayCount + " days";
	}

	/**
	 * Returns the KoL calendar month associated with the
	 * given date in the real world.
	 */

	public static final int getCalendarMonth( Date time )
	{	return convertCalendarDayToArray( getCalendarDay( time ) )[0];
	}

	/**
	 * Returns whether or not the given day's most important
	 * attribute is being a holiday.
	 */

	public static boolean isHoliday( Date time )
	{	return SPECIAL[ getCalendarDay( time ) ] == SP_HOLIDAY;
	}

	public static boolean isRealLifeHoliday( Date time )
	{	return getRealLifeHoliday( DATED_FILENAME_FORMAT.format( time ) ) != null;
	}

	/**
	 * Returns whether or not the given day's most important
	 * attribute is being a muscle day.  Note that this ranks
	 * behind being a holiday, so HOLIDAYS which are also stat
	 * days (Halloween and Oyster Egg Day, for example), will
	 * not be recognized as "stat days" in this method.
	 */

	public static boolean isMuscleDay( Date time )
	{	return SPECIAL[ getCalendarDay( time ) ] == SP_MUSDAY;
	}

	/**
	 * Returns whether or not the given day's most important
	 * attribute is being a mysticality day.  Note that this ranks
	 * behind being a holiday, so HOLIDAYS which are also stat
	 * days (Halloween and Oyster Egg Day, for example), will
	 * not be recognized as "stat days" in this method.
	 */

	public static boolean isMysticalityDay( Date time )
	{	return SPECIAL[ getCalendarDay( time ) ] == SP_MYSDAY;
	}

	/**
	 * Returns whether or not the given day's most important
	 * attribute is being a moxie day.  Note that this ranks
	 * behind being a holiday, so HOLIDAYS which are also stat
	 * days (Halloween and Oyster Egg Day, for example), will
	 * not be recognized as "stat days" in this method.
	 */

	public static boolean isMoxieDay( Date time )
	{	return SPECIAL[ getCalendarDay( time ) ] == SP_MOXDAY;
	}

	/**
	 * Returns a complete list of all holiday predictions for
	 * the given day, as an array.
	 */

	public static final String [] getHolidayPredictions( Date time )
	{
		List predictionsList = new ArrayList();
		int currentCalendarDay = getCalendarDay( time );

		int [] calendarDayAsArray;

		for ( int i = 0; i < 96; ++i )
		{
			if ( SPECIAL[i] == SP_HOLIDAY )
			{
				calendarDayAsArray = convertCalendarDayToArray( i );
				int currentEstimate = (i - currentCalendarDay + 96) % 96;

				String holiday = HOLIDAYS[ calendarDayAsArray[0] ][ calendarDayAsArray[1] ];

				String testDate = null;
				String testResult = null;

				Calendar holidayTester = Calendar.getInstance();
				holidayTester.setTime( time );

				for ( int j = 0; j < currentEstimate; ++j )
				{
					testDate = DATED_FILENAME_FORMAT.format( holidayTester.getTime() );
					testResult = getRealLifeHoliday( testDate );

					if ( holiday != null && testResult != null && testResult.equals( holiday ) )
						currentEstimate = j;

					holidayTester.add( Calendar.DATE, 1 );
				}

				predictionsList.add( HOLIDAYS[ calendarDayAsArray[0] ][ calendarDayAsArray[1] ] + ": " +
					getDayCountAsString( currentEstimate ) );
			}
		}

		// If today is a real life holiday that doesn't map to a KoL
		// holiday, list it here.

		if ( SPECIAL[ getCalendarDay( time ) ] != SP_HOLIDAY )
		{
			String holiday = getRealLifeOnlyHoliday( DATED_FILENAME_FORMAT.format( time ) );
			if ( holiday != null )
				predictionsList.add( holiday + ": today" );
		}

		String [] predictionsArray = new String[ predictionsList.size() ];
		predictionsList.toArray( predictionsArray );
		return predictionsArray;
	}

	public static final String getHoliday( Date time )
	{	return getHoliday( time, false );
	}

	/**
	 * Returns the KoL holiday associated with the given
	 * date in the real world.
	 */

	public static final String getHoliday( Date time, boolean showPrediction )
	{
		int calendarDay = getCalendarDay( time );
		int [] calendarDayAsArray = convertCalendarDayToArray( calendarDay );

		String gameHoliday = HOLIDAYS[ calendarDayAsArray[0] ][ calendarDayAsArray[1] ];
		String realHoliday = getRealLifeHoliday( DATED_FILENAME_FORMAT.format( time ) );

		if ( showPrediction && realHoliday == null )
		{
			if ( gameHoliday != null )
				gameHoliday = gameHoliday + " today";

			for ( int i = 1; gameHoliday == null; ++i )
			{
				calendarDayAsArray = convertCalendarDayToArray( calendarDay + i % 96 );
				gameHoliday = HOLIDAYS[ calendarDayAsArray[0] ][ calendarDayAsArray[1] ];

				if ( gameHoliday != null )
				{
					if ( i == 1 )
						gameHoliday = gameHoliday + " tomorrow";
					else
						gameHoliday = getDayCountAsString( i ) + " until " + gameHoliday;

				}
			}
		}

		return gameHoliday == null && realHoliday == null ? "No known holiday today." :
			gameHoliday == null ? realHoliday : realHoliday == null ? gameHoliday :
			realHoliday + " / " + gameHoliday;
	}

	private static String cachedYear = "";
	private static String easter = "";

	public static String getRealLifeHoliday( String stringDate )
	{
		String currentYear = stringDate.substring( 0, 4 );
		if ( !currentYear.equals( cachedYear ) )
		{
			cachedYear = currentYear;
			Calendar holidayFinder = Calendar.getInstance( TimeZone.getTimeZone( "GMT-5" ) );

			// Apparently, Easter isn't the second Sunday in April;
			// it actually depends on the occurrence of the first
			// ecclesiastical full moon after the Spring Equinox
			// (http://aa.usno.navy.mil/faq/docs/easter.html)

			int y = parseInt( currentYear );
			int c = y / 100;
			int n = y - 19 * ( y / 19 );
			int k = ( c - 17 ) / 25;
			int i = c - c / 4 - ( c - k ) / 3 + 19 * n + 15;
			i = i - 30 * ( i / 30 );
			i = i - ( i / 28 ) * ( 1 - ( i / 28 ) * ( 29 / ( i + 1 ) ) * ( ( 21 - n ) / 11 ) );
			int j = y + y / 4 + i + 2 - c + c / 4;
			j = j - 7 * ( j / 7 );
			int l = i - j;
			int m = 3 + ( l + 40 ) / 44;
			int d = l + 28 - 31 * ( m / 4 );

			holidayFinder.set( Calendar.YEAR, y );
			holidayFinder.set( Calendar.MONTH, m - 1 );
			holidayFinder.set( Calendar.DAY_OF_MONTH, d );

			easter = DATED_FILENAME_FORMAT.format( holidayFinder.getTime() );
		}

		// Real-life holiday list borrowed from JRSiebz's
		// variables for HOLIDAYS on the KoL JS Almanac
		// (http://home.cinci.rr.com/jrsiebz/KoL/almanac.html)

		if ( stringDate.endsWith( "0214" ) )
			return "Valentine's Day";

		if ( stringDate.endsWith( "0317" ) )
			return "St. Sneaky Pete's Day";

		if ( stringDate.equals( easter ) )
			return "Oyster Egg Day";

		if ( stringDate.endsWith( "1031" ) )
			return "Halloween";

		return getRealLifeOnlyHoliday( stringDate );
	}

	public static String getRealLifeOnlyHoliday( String stringDate )
	{
		if ( stringDate.endsWith( "0202" ) )
			return "Groundhog Day";

		if ( stringDate.endsWith( "0401" ) )
			return "April Fool's Day";

		if ( stringDate.endsWith( "0919" ) )
			return "Talk Like a Pirate Day";

		if ( stringDate.endsWith( "1225" ) )
			return "Crimbo";

		if ( stringDate.endsWith( "1022" ) )
			return "Holatuwol's Birthday";

		if ( stringDate.endsWith( "0923" ) )
			return "Veracity's Birthday";

		return null;
	}

	public static final void addPredictionHTML( StringBuffer displayHTML, Date today, int phaseStep )
	{	addPredictionHTML( displayHTML, today, phaseStep, true );
	}

	public static final void addPredictionHTML( StringBuffer displayHTML, Date today, int phaseStep, boolean addStatDays )
	{
		// Next display the upcoming stat days.

		if ( addStatDays )
		{
			displayHTML.append( "<nobr><b>Muscle Day</b>:&nbsp;" );
			displayHTML.append( getDayCountAsString( Math.min( (24 - phaseStep) % 16, (25 - phaseStep) % 16 ) ) );
			displayHTML.append( "</nobr><br>" );

			displayHTML.append( "<nobr><b>Mysticality Day</b>:&nbsp;" );
			displayHTML.append( getDayCountAsString( Math.min( (20 - phaseStep) % 16, (28 - phaseStep) % 16 ) ) );
			displayHTML.append( "</nobr><br>" );

			displayHTML.append( "<nobr><b>Moxie Day</b>:&nbsp;" );
			displayHTML.append( getDayCountAsString( Math.min( (16 - phaseStep) % 16, (31 - phaseStep) % 16 ) ) );
			displayHTML.append( "</nobr><br>&nbsp;<br>" );
		}

		// Next display the upcoming holidays.  This is done
		// through loop calculations in order to minimize the
		// amount of code done to handle individual holidays.

		String [] holidayPredictions = getHolidayPredictions( today );
		for ( int i = 0; i < holidayPredictions.length; ++i )
		{
			displayHTML.append( "<nobr><b>" );
			displayHTML.append( holidayPredictions[i].replaceAll( ":", ":</b>&nbsp;" ) );
			displayHTML.append( "</nobr><br>" );
		}
	}
}
