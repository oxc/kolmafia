package net.sourceforge.kolmafia.session;

import java.io.PrintStream;

import net.sourceforge.kolmafia.AdventureResult;
import net.sourceforge.kolmafia.KoLCharacter;
import net.sourceforge.kolmafia.KoLConstants;
import net.sourceforge.kolmafia.KoLmafia;
import net.sourceforge.kolmafia.RequestLogger;
import net.sourceforge.kolmafia.RequestThread;
import net.sourceforge.kolmafia.StaticEntity;
import net.sourceforge.kolmafia.objectpool.ItemPool;

import net.sourceforge.kolmafia.request.CreateItemRequest;
import net.sourceforge.kolmafia.request.UntinkerRequest;
import net.sourceforge.kolmafia.request.UseItemRequest;

import net.sourceforge.kolmafia.persistence.Preferences;

public class ValhallaManager
{
	public static void preAscension()
	{
		// Untinker the Bitchin' meatcar

		if ( InventoryManager.hasItem( ItemPool.BITCHIN_MEATCAR ) )
		{
			new UntinkerRequest( ItemPool.BITCHIN_MEATCAR ).run();
		}

		// Create a badass belt

		CreateItemRequest belt = CreateItemRequest.getInstance( ItemPool.BADASS_BELT );
		if ( belt != null && belt.getQuantityPossible() > 0 )
		{
			belt.setQuantityNeeded( belt.getQuantityPossible() );
			belt.run();
		}

		// Use any 31337 scrolls.

		AdventureResult scroll = ItemPool.get( ItemPool.ELITE_SCROLL, 1 );
		int count = scroll.getCount( KoLConstants.inventory );
		if ( count > 0 )
		{
			new UseItemRequest( scroll.getInstance( count ) ).run();
		}

		// Trade in gunpowder.

		if ( InventoryManager.hasItem( ItemPool.GUNPOWDER ) )
		{
			BreakfastManager.visitPyro();
		}
	}

	public static void postAscension()
	{
		KoLCharacter.reset();

		RequestThread.openRequestSequence();
		Preferences.setInteger( "lastBreakfast", -1 );

		KoLmafia.resetCounters();
		ValhallaManager.resetPerAscensionCounters();

		StaticEntity.getClient().refreshSession( false );
		StaticEntity.getClient().resetSession();
		KoLConstants.conditions.clear();

		// Based on your class, you get some basic
		// items once you ascend.

		String type = KoLCharacter.getClassType();
		if ( type.equals( KoLCharacter.SEAL_CLUBBER ) )
		{
			ResultProcessor.processResult( new AdventureResult( "seal-skull helmet", 1, false ), false );
			ResultProcessor.processResult( new AdventureResult( "seal-clubbing club", 1, false ), false );
		}
		else if ( type.equals( KoLCharacter.TURTLE_TAMER ) )
		{
			ResultProcessor.processResult( new AdventureResult( "helmet turtle", 1, false ), false );
			ResultProcessor.processResult( new AdventureResult( "turtle totem", 1, false ), false );
		}
		else if ( type.equals( KoLCharacter.PASTAMANCER ) )
		{
			ResultProcessor.processResult( new AdventureResult( "pasta spoon", 1, false ), false );
			ResultProcessor.processResult( new AdventureResult( "ravioli hat", 1, false ), false );
		}
		else if ( type.equals( KoLCharacter.SAUCEROR ) )
		{
			ResultProcessor.processResult( new AdventureResult( "saucepan", 1, false ), false );
			ResultProcessor.processResult( new AdventureResult( "spices", 1, false ), false );
		}
		else if ( type.equals( KoLCharacter.DISCO_BANDIT ) )
		{
			ResultProcessor.processResult( new AdventureResult( "disco ball", 1, false ), false );
			ResultProcessor.processResult( new AdventureResult( "disco mask", 1, false ), false );
		}
		else if ( type.equals( KoLCharacter.ACCORDION_THIEF ) )
		{
			ResultProcessor.processResult( new AdventureResult( "mariachi pants", 1, false ), false );
			ResultProcessor.processResult( new AdventureResult( "stolen accordion", 1, false ), false );
		}

		// Note the information in the session log
		// for recording purposes.

		MoodManager.setMood( "apathetic" );
		PrintStream sessionStream = RequestLogger.getSessionStream();

		sessionStream.println();
		sessionStream.println();
		sessionStream.println( "=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=" );
		sessionStream.println( "	   Beginning New Ascension	     " );
		sessionStream.println( "=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=" );
		sessionStream.println();

		sessionStream.println( "Ascension #" + KoLCharacter.getAscensions() + ":" );

		if ( KoLCharacter.isHardcore() )
		{
			sessionStream.print( "Hardcore " );
		}
		else
		{
			sessionStream.print( "Softcore " );
		}

		if ( KoLCharacter.canEat() && KoLCharacter.canDrink() )
		{
			sessionStream.print( "No-Path " );
		}
		else if ( KoLCharacter.canEat() )
		{
			sessionStream.print( "Teetotaler " );
		}
		else if ( KoLCharacter.canDrink() )
		{
			sessionStream.print( "Boozetafarian " );
		}
		else
		{
			sessionStream.print( "Oxygenarian " );
		}

		sessionStream.println( KoLCharacter.getClassType() );
		sessionStream.println();
		sessionStream.println();

		RequestLogger.printList( KoLConstants.availableSkills, sessionStream );
		sessionStream.println();

		sessionStream.println( "=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=" );

		sessionStream.println();
		sessionStream.println();

		RequestThread.closeRequestSequence();
	}

	public static final void resetPerAscensionCounters()
	{
		Preferences.setInteger( "currentBountyItem", 0 );
		Preferences.setString( "currentHippyStore", "none" );
		Preferences.setString( "currentWheelPosition", "muscle" );
		Preferences.setInteger( "fratboysDefeated", 0 );
		Preferences.setInteger( "guyMadeOfBeesCount", 0 );
		Preferences.setBoolean( "guyMadeOfBeesDefeated", false );
		Preferences.setInteger( "hippiesDefeated", 0 );
		Preferences.setString( "trapperOre", "chrome" );
	}

}
