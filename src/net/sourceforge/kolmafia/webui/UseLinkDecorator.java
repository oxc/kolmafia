/**
 * Copyright (c) 2005-2018, KoLmafia development team
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

package net.sourceforge.kolmafia.webui;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.java.dev.spellcast.utilities.SortedListModel;

import net.sourceforge.kolmafia.AdventureResult;
import net.sourceforge.kolmafia.KoLCharacter;
import net.sourceforge.kolmafia.KoLConstants;
import net.sourceforge.kolmafia.KoLConstants.CraftingRequirements;
import net.sourceforge.kolmafia.KoLConstants.CraftingType;
import net.sourceforge.kolmafia.Modifiers;
import net.sourceforge.kolmafia.Speculation;

import net.sourceforge.kolmafia.objectpool.EffectPool;
import net.sourceforge.kolmafia.objectpool.ItemPool;

import net.sourceforge.kolmafia.persistence.ConcoctionDatabase;
import net.sourceforge.kolmafia.persistence.ConsumablesDatabase;
import net.sourceforge.kolmafia.persistence.EffectDatabase;
import net.sourceforge.kolmafia.persistence.EquipmentDatabase;
import net.sourceforge.kolmafia.persistence.ItemDatabase;
import net.sourceforge.kolmafia.persistence.QuestDatabase;
import net.sourceforge.kolmafia.persistence.QuestDatabase.Quest;

import net.sourceforge.kolmafia.preferences.Preferences;

import net.sourceforge.kolmafia.request.CreateItemRequest;
import net.sourceforge.kolmafia.request.EquipmentRequest;
import net.sourceforge.kolmafia.request.FightRequest;
import net.sourceforge.kolmafia.request.GenericRequest;
import net.sourceforge.kolmafia.request.IslandRequest;
import net.sourceforge.kolmafia.request.OrcChasmRequest;
import net.sourceforge.kolmafia.request.UseItemRequest;

import net.sourceforge.kolmafia.session.ChoiceManager;
import net.sourceforge.kolmafia.session.EquipmentManager;
import net.sourceforge.kolmafia.session.InventoryManager;
import net.sourceforge.kolmafia.session.Limitmode;
import net.sourceforge.kolmafia.session.ResultProcessor;
import net.sourceforge.kolmafia.session.TurnCounter;

import net.sourceforge.kolmafia.textui.command.SpeculateCommand;

import net.sourceforge.kolmafia.utilities.StringUtilities;

public abstract class UseLinkDecorator
{
	private static final StringBuffer deferred = new StringBuffer();

	public static final void decorate( final String location, final StringBuffer buffer )
	{
		// You ain't doin' nothin' in Valhalla
		if ( location.startsWith( "afterlife.php" ) )
		{
			return;
		}

		// If you are Ed in the Underworld, you have to wait until you
		// are on the surface again before you use anything
		String limitMode = KoLCharacter.getLimitmode();
		if ( limitMode == Limitmode.ED )
		{
			return;
		}

		// You CAN buy from the mall in Hardcore or Ronin, but any
		// results go into Hagnk's storage.
		if ( ( location.startsWith( "mallstore.php" ) || location.startsWith( "backoffice.php" ) ) &&
		     !KoLCharacter.canInteract() )
		{
			return;
		}

		boolean inCombat = location.startsWith( "fight.php" );
		boolean inChoice = location.startsWith( "choice.php" );
		if ( !inCombat && !inChoice &&
		     buffer.indexOf( "You acquire" ) == -1 &&
		     buffer.indexOf( "O hai, I made dis" ) == -1 )
		{
			return;
		}

		// Defer use links until later if this isn't the final combat/choice page
		String macro = FightRequest.lastMacroUsed;
		boolean usedNativeMacro = macro != null && !macro.equals( "" ) && !macro.equals( "0" );
		boolean usedMafiaMacro = location.contains( "action=done" );
		boolean usedMacro = inCombat && ( usedNativeMacro || usedMafiaMacro );

		// Some combats lead to a non-optional choice
		boolean duringCombat = inCombat && ( FightRequest.getCurrentRound() != 0 || buffer.indexOf( "choice.php" ) != -1 );
		// Some choices lead to a non-optional choice
		boolean duringChoice = inChoice && buffer.indexOf( "choice.php" ) != -1 && !ChoiceManager.canWalkAway();

		// If we are currently in a combat or choice, we should consider deferring
		boolean deferrable = inCombat || inChoice;

		// If we are forced to continue to be in a combat or choice, continue deferring
		boolean deferring = duringCombat || duringChoice;

		String text = buffer.toString();
		buffer.setLength( 0 );

		boolean poetry = inCombat && ( FightRequest.haiku || FightRequest.anapest );

		if ( poetry )
		{
			UseLinkDecorator.addPoeticUseLinks( location, text, buffer, deferring );
		}
		else
		{
			UseLinkDecorator.addNormalUseLinks( location, text, buffer, deferring, usedMacro );
		}
		
		if ( inCombat )
		{
			StringUtilities.singleStringReplace( buffer,
				"A sticker falls off your weapon, faded and torn.",
				"A sticker falls off your weapon, faded and torn. <font size=1>" +
				"[<a href=\"bedazzle.php\">bedazzle</a>]</font>" );
		}
		
		if ( deferring )
		{	// discard all changes, the links aren't usable yet
			buffer.setLength( 0 );
			buffer.append( text );
		}
		
		else if ( deferrable )
		{
			int pos = buffer.lastIndexOf( "</table>" );
			if ( pos == -1 )
			{
				return;
			}
			text = buffer.substring( pos );
			buffer.setLength( pos );
			if ( inCombat )
			{
				buffer.append( "</table><table><tr><td>" );
				if ( usedNativeMacro )
				{
					buffer.append( "<form method=POST action=\"account_combatmacros.php\"><input type=HIDDEN name=action value=edit><input type=HIDDEN name=macroid value=\"" );
					buffer.append( macro );
					buffer.append( "\"><input type=SUBMIT value=\"Edit last macro\"></form>" );
					FightRequest.lastMacroUsed = "";
				}
				else
				{
					buffer.append( "[<a href=\"/account_combatmacros.php\">edit macros</a>]" );
				}
				buffer.append( "</td></tr>" );
			}
			
			if ( UseLinkDecorator.deferred.length() > 0 )
			{
				String tag = inCombat ? "Found in this fight" : "Previously seen";
				buffer.append( "</table><table><tr><td colspan=2>" );
				buffer.append( tag );
				buffer.append( ":</td></tr>" );
				buffer.append( UseLinkDecorator.deferred );
				UseLinkDecorator.deferred.setLength( 0 );
			}

			buffer.append( text );
		}
	}

	// <table class="item" style="float: none" rel="id=2334&s=0&q=1&d=0&g=0&t=0&n=1&m=0&p=0&u=."><tr><td><img src="http://images.kingdomofloathing.com/itemimages/macguffin.gif" alt="Holy MacGuffin" title="Holy MacGuffin" class=hand onClick='descitem(302128482)'></td><td valign=center class=effect>You acquire an item: <b>Holy MacGuffin</b></td></tr></table>

	private static final Pattern ACQUIRE_PATTERN =
		Pattern.compile( "(You acquire|O hai, I made dis)([^<]*?)<b>(.*?)</b>(.*?)</td>", Pattern.DOTALL );

	private static final Pattern BOUNTY_COUNT_PATTERN = Pattern.compile( "\\((\\d+) of (\\d+) found\\)" );
	
	private static final void addNormalUseLinks( String location, String text, StringBuffer buffer, boolean deferring, boolean usedMacro )
	{
		// Get a list of of items via "rel" strings
		LinkedList<AdventureResult> items = ResultProcessor.parseItems( text );

		// Get a list of effects via descid
		LinkedList<AdventureResult> effects = ResultProcessor.parseEffects( text );

		Matcher useLinkMatcher = ACQUIRE_PATTERN.matcher( text );

		int specialLinkId = 0;
		String specialLinkText = null;

		while ( useLinkMatcher.find() )
		{
			// See if it's an effect
			if ( UseLinkDecorator.addEffectLink( location, useLinkMatcher, buffer, effects ) )
			{
				continue;
			}

			// Get type of acquisition
			String type = useLinkMatcher.group( 2 );
			int pos = buffer.length();
			boolean link = false;
			String comment = useLinkMatcher.group( 4 );

			if ( comment.contains( "Hagnk" ) || comment.contains( "automatically equipped" ) )
			{
				// If the item ended up in Hagnk's storage or
				// was automatically equipped, no use link.
				continue;
			}

			// Special handling for bounty items
			if ( type.contains( "bounty item" ) )
			{
				// Add link for visiting bounty hunter if the last bounty drops
				Matcher matcher = UseLinkDecorator.BOUNTY_COUNT_PATTERN.matcher( text );
				if ( matcher.find() )
				{
					String bountyCount = matcher.group( 1 );
					String bountyTotal = matcher.group( 2 );
					if ( bountyCount.equals( bountyTotal ) )
					{
						UseLink useLink = new UseLink( "return to hunter", "bounty.php" );
						useLinkMatcher.appendReplacement( buffer, "$1$2<b>$3</b> "+ useLink.getItemHTML() + "$4" );
						link = true;
					}
				}
			}
			else
			{
				int itemId = -1;
				int itemCount = 1;
				String itemName = useLinkMatcher.group( 3 );

				if ( itemName.equals( "Tales of the West:  Cow Punching" ) )
				{
					// Accommodate KoL bug: extra space in name
					itemName = "Tales of the West: Cow Punching";
				}

				int spaceIndex = itemName.indexOf( " " );
				if ( spaceIndex != -1 && !type.contains( ":" ) )
				{
					itemCount = StringUtilities.parseInt( itemName.substring( 0, spaceIndex ) );
					itemName = itemName.substring( spaceIndex + 1 );
				}

				AdventureResult item = items.size() == 0 ? null : items.getFirst();

				if ( item != null && itemName.equals( item.getName() ) )
				{
					items.removeFirst();
					itemId = item.getItemId();
					itemCount = item.getCount();
				}
				else
				{
					itemId = ItemDatabase.getItemId( itemName, itemCount, itemCount > 1 );
				}

				if ( itemId == -1 )
				{
					continue;
				}

				// Certain items get use special links to minimize the
				// amount of scrolling to find the item again.

				if ( location.startsWith( "inventory.php" ) || ( location.startsWith( "inv_use.php" ) && location.contains( "ajax=1" ) ) )
				{
					switch ( itemId )
					{
					case ItemPool.FOIL_BOW:
					case ItemPool.FOIL_RADAR:
					case ItemPool.FOIL_CAT_EARS:
						specialLinkId = itemId;
						specialLinkText = "fold";
						break;
					}
				}

				// Possibly append a use link
				link = UseLinkDecorator.addUseLink( itemId, itemCount, location, useLinkMatcher, text, buffer );
			}

			// If we added no link, copy in the text verbatim
			if ( !link )
			{
				useLinkMatcher.appendReplacement( buffer, "$1$2<b>$3</b>$4</td>" );
			}

			// If we are currently deferring use links or have
			// already deferred some during this combat, append to
			// list of items previously seen.
			// 
			// During macro execution, append everything found, so
			// they accumulate at the bottom of the screen.

			if ( usedMacro || deferring || UseLinkDecorator.deferred.length() > 0 )
			{	// Find where the replacement was appended
				String match =
					useLinkMatcher.group( 1 ) +
					useLinkMatcher.group( 2 ) +
					"<b>" +
					useLinkMatcher.group( 3 );
				pos = buffer.indexOf( match, pos );
				if ( pos == -1 )
				{
					continue;
				}

				// Find start of table containing it
				pos = buffer.lastIndexOf( "<table", pos );
				if ( pos == -1 )
				{
					continue;
				}

				UseLinkDecorator.deferred.append( "<tr>" );
				UseLinkDecorator.deferred.append( buffer.substring( pos ) );
				UseLinkDecorator.deferred.append( "</table>" );
				UseLinkDecorator.deferred.append( "</tr>" );
			}
		}

		useLinkMatcher.appendTail( buffer );

		if ( !deferring && specialLinkText != null )
		{
			StringUtilities.singleStringReplace(
				buffer,
				"</center></blockquote>",
				"<p><center><a href=\"inv_use.php?pwd=" + GenericRequest.passwordHash + "&which=2&whichitem=" + specialLinkId + "\">[" + specialLinkText + " it again]</a></center></blockquote>" );
		}
	}

	// <img src="http://images.kingdomofloathing.com/itemimages/jerkcicle.gif" alt="dangerous jerkcicle" title="dangerous jerkcicle" class=hand onClick='descitem(726861308)'>
	private static final Pattern POETIC_ACQUIRE_PATTERN =
		Pattern.compile( "<img[^>]*?descitem\\((\\d+)\\)'>", Pattern.DOTALL );

	private static final void addPoeticUseLinks( String location, String text, StringBuffer buffer, boolean deferring )
	{
		Matcher useLinkMatcher = POETIC_ACQUIRE_PATTERN.matcher( text );

		while ( useLinkMatcher.find() )
		{
			String descId = useLinkMatcher.group( 1 );
			String itemName = ItemDatabase.getItemName( descId );
			int itemId = ItemDatabase.getItemIdFromDescription( descId );

			if ( itemId == -1 )
			{
				continue;
			}

			UseLinkDecorator.deferred.append( "<tr><td>" );
			UseLinkDecorator.deferred.append( useLinkMatcher.group( 0 ) );
			UseLinkDecorator.deferred.append( "</td><td>You acquire an item: <b>" );
			UseLinkDecorator.deferred.append( itemName );
			UseLinkDecorator.deferred.append( "</b>" );

			UseLink link = UseLinkDecorator.generateUseLink( itemId, 1, location, text );

			if ( link != null )
			{
				UseLinkDecorator.deferred.append( " " );
				UseLinkDecorator.deferred.append( link.getItemHTML() );
			}

			UseLinkDecorator.deferred.append( "</td></tr>" );
		}

		// Copy the text unchanged into the buffer
		buffer.append( text );
	}

	private static final CraftingType shouldAddCreateLink( int itemId, String location )
	{
		if ( location == null || ( location.contains( "craft.php" ) && !location.contains( "pulverize" ) ) )
		{
			return CraftingType.NOCREATE;
		}

		// Retrieve the known ingredient uses for the item.
		SortedListModel creations = ConcoctionDatabase.getKnownUses( itemId );
		if ( creations.isEmpty() )
		{
			return CraftingType.NOCREATE;
		}

		// Skip items which are multi-use.
		int consumeMethod = ItemDatabase.getConsumptionType( itemId );
		if ( consumeMethod == KoLConstants.CONSUME_MULTIPLE )
		{
			return CraftingType.NOCREATE;
		}

		switch ( itemId )
		{
		// If you find the wooden stakes, you want to equip them
		case ItemPool.WOODEN_STAKES:
			return CraftingType.NOCREATE;

		// If you find goat cheese, let the trapper link handle it.
		case ItemPool.GOAT_CHEESE:
			return CraftingType.NOCREATE;

		// If you find ore, let the trapper link handle it.
		case ItemPool.LINOLEUM_ORE:
		case ItemPool.ASBESTOS_ORE:
		case ItemPool.CHROME_ORE:
			return CraftingType.NOCREATE;

		// Dictionaries and bridges should link to the chasm quest.
		case ItemPool.DICTIONARY:
		case ItemPool.BRIDGE:
			return CraftingType.NOCREATE;

		// The eyepatch can be combined, but is usually an outfit piece
		// The dreadsack can be combined, but is usually an outfit piece
		// The frilly skirt is usually used for the frathouse blueprints
		case ItemPool.EYEPATCH:
		case ItemPool.DREADSACK:
		case ItemPool.FRILLY_SKIRT:
			return CraftingType.NOCREATE;

		// Spooky Fertilizer CAN be cooked, but almost always is used
		// for with the spooky temple map.
		case ItemPool.SPOOKY_FERTILIZER:
			return CraftingType.NOCREATE;

		// Enchanted beans are primarily used for the beanstalk quest.
		case ItemPool.ENCHANTED_BEAN:
			if ( KoLCharacter.getLevel() >= 10 && !InventoryManager.hasItem( ItemPool.SOCK ) )
			{
				return CraftingType.NOCREATE;
			}
			break;
		}

		for ( int i = 0; i < creations.size(); ++i )
		{
			AdventureResult creation = (AdventureResult) creations.get( i );
			CraftingType mixingMethod = ConcoctionDatabase.getMixingMethod( creation );
			EnumSet<CraftingRequirements> requirements = ConcoctionDatabase.getRequirements( creation.getItemId() );
			if ( !ConcoctionDatabase.isPermittedMethod( mixingMethod, requirements ) )
			{
				continue;
			}

			// Only accept if it's a creation method that the
			// editor kit currently understands and links.

			switch ( mixingMethod )
			{
			case COMBINE:
			case ACOMBINE:
			case MIX:
			case MIX_FANCY:
			case COOK:
			case COOK_FANCY:
			case JEWELRY:
				break;
			default:
				continue;
			}

			CreateItemRequest irequest = CreateItemRequest.getInstance( creation );
			if ( irequest != null && irequest.getQuantityPossible() > 0 )
			{
				return mixingMethod;
			}
		}

		return CraftingType.NOCREATE;
	}

	private static final boolean addEffectLink( String location, Matcher useLinkMatcher, StringBuffer buffer, LinkedList<AdventureResult> effects )
	{
		String message = useLinkMatcher.group(0);
		if ( !message.contains( "You acquire an effect" ) )
		{
			return false;
		}

		String effectName = useLinkMatcher.group(3);
		AdventureResult effect = effects.size() == 0 ? null : effects.getFirst();

		if ( effect != null && effectName.equals( effect.getName() ) )
		{
			effects.removeFirst();
		}
		else
		{
			effect = EffectPool.get( EffectDatabase.getEffectId( effectName ), 1 );
		}

		UseLink link = null;

		switch ( effect.getEffectId() )
		{
		case EffectPool.FILTHWORM_LARVA_STENCH:
			link = new UseLink( 0, "feeding chamber", "adventure.php?snarfblat=128" );
			break;
		case EffectPool.FILTHWORM_DRONE_STENCH:
			link = new UseLink( 0, "guards' chamber", "adventure.php?snarfblat=129" );
			break;
		case EffectPool.FILTHWORM_GUARD_STENCH:
			link = new UseLink( 0, "queen's chamber", "adventure.php?snarfblat=130" );
			break;
		case EffectPool.KNOB_GOBLIN_PERFUME:
			link = new UseLink( 0, "throne room", "cobbsknob.php?action=throneroom" );
			break;
		case EffectPool.DOWN_THE_RABBIT_HOLE:
			link = new UseLink( 0, "rabbit hole", "place.php?whichplace=rabbithole" );
			break;
		case EffectPool.TRANSPONDENT:
			link = new UseLink( 0, "spaaace", "spaaace.php?arrive=1" );
			break;
		case EffectPool.STONE_FACED:
			link = new UseLink( 0, "hidden temple", "adventure.php?snarfblat=280" );
			break;
		case EffectPool.DIS_ABLED:
			link = new UseLink( 0, "portal to dis", "suburbandis.php" );
			break;
		default:
			// There are several effect names which are also items.
			// We know that we're dealing with an effect here, so there's
			// no point in also checking for an item match.
			return true;
		}

		String useType = link.getUseType();
		String useLocation = link.getUseLocation();

		useLinkMatcher.appendReplacement(
			buffer,
			"$1$2<b>$3</b> <font size=1>[<a href=\"" + useLocation + "\">" + useType + "</a>]</font></td>" + "$4"  );
		return true;
	}

	private static final boolean addUseLink( int itemId, int itemCount, String location, Matcher useLinkMatcher, String text, StringBuffer buffer )
	{
		UseLink link = UseLinkDecorator.generateUseLink( itemId, itemCount, location, text );

		if ( link == null )
		{
			return false;
		}

		useLinkMatcher.appendReplacement( buffer, "$1$2<b>$3</b> "+ link.getItemHTML() + "$4"  );

		buffer.append( "</td>" );
		return true;
	}

	private static final UseLink generateUseLink( int itemId, int itemCount, String location, String text )
	{
		int consumeMethod = ItemDatabase.getConsumptionType( itemId );
		CraftingType mixingMethod = shouldAddCreateLink( itemId, location );

		if ( mixingMethod != CraftingType.NOCREATE )
		{
			return getCreateLink( itemId, itemCount, mixingMethod );
		}

		if ( consumeMethod == KoLConstants.NO_CONSUME )
		{
			return getNavigationLink( itemId, location );
		}

		return getUseLink( itemId, itemCount, location, consumeMethod, text );
	}

	private static final UseLink getCreateLink( final int itemId, final int itemCount, final CraftingType mixingMethod )
	{
		switch ( mixingMethod )
		{
		case COMBINE:
		case ACOMBINE:
		case JEWELRY:
			return new UseLink( itemId, itemCount, "combine", "craft.php?mode=combine&a=" );

		case MIX:
		case MIX_FANCY:
			return new UseLink( itemId, itemCount, "mix", "craft.php?mode=cocktail&a=" );

		case COOK:
		case COOK_FANCY:
			return new UseLink( itemId, itemCount, "cook", "craft.php?mode=cook&a=" );
		}

		return null;
	}

	private static final UseLink getUseLink( int itemId, int itemCount, String location, int consumeMethod, final String text )
	{
		if ( !ConsumablesDatabase.meetsLevelRequirement( ItemDatabase.getItemName( itemId ) ) )
		{
			return null;
		}

		boolean combatResults = location.startsWith( "fight.php" );
		
		switch ( consumeMethod )
		{
		case KoLConstants.GROW_FAMILIAR:

			if ( itemId  == ItemPool.MOSQUITO_LARVA )
			{
				if ( KoLCharacter.isEd() )
				{
					return new UseLink( itemId, "Amun", "council.php" );
				}
				else
				{
					return new UseLink( itemId, "council", "council.php" );
				}
			}

			if ( KoLCharacter.isPicky() )
			{
				return null;
			}

			if ( KoLCharacter.inBeecore() && ItemDatabase.unusableInBeecore( itemId ) )
			{
				return null;
			}

			return new UseLink( itemId, "grow", "inv_familiar.php?whichitem=" );

		case KoLConstants.CONSUME_EAT:

			switch ( itemId )
			{
			case ItemPool.GOAT_CHEESE:
				return new UseLink( itemId, InventoryManager.getCount( itemId ), "place.php?whichplace=mclargehuge&action=trappercabin" );

			case ItemPool.FORTUNE_COOKIE:
			{
				ArrayList<UseLink> uses = new ArrayList<UseLink>();

				if ( KoLCharacter.canEat() )
				{
					uses.add( new UseLink( itemId, itemCount, "eat", "inv_eat.php?which=1&whichitem=" ) );
				}

				uses.add( new UseLink( itemId, itemCount, "smash", "inv_use.php?which=1&whichitem=" ) );

				if  ( uses.size() == 1 )
				{
					return uses.get( 0 );
				}

				return new UsesLink( uses.toArray( new UseLink[ uses.size() ] ) );
			}

			case ItemPool.HOT_WING:
			{
				ArrayList<UseLink> uses = new ArrayList<UseLink>();

				if ( KoLCharacter.canEat() )
				{
					uses.add( new UseLink( itemId, itemCount, "eat", "inv_eat.php?which=1&whichitem=" ) );
				}

				uses.add( new UseLink( itemId, InventoryManager.getCount( itemId ) ) );

				if ( uses.size() == 1 )
				{
					return uses.get( 0 );
				}

				return new UsesLink( uses.toArray( new UseLink[ uses.size() ] ) );
			}
			case ItemPool.SNOW_BERRIES:
			case ItemPool.ICE_HARVEST:
			{
				ArrayList<UseLink> uses = new ArrayList<UseLink>();

				if ( KoLCharacter.canEat() )
				{
					uses.add( new UseLink( itemId, itemCount, "eat", "inv_eat.php?which=1&whichitem=" ) );
				}

				uses.add( new UseLink( itemId, 1, "make stuff", "shop.php?whichshop=snowgarden" ) );

				if ( uses.size() == 1 )
				{
					return uses.get( 0 );
				}

				return new UsesLink( uses.toArray( new UseLink[ uses.size() ] ) );
			}
			}

			if ( !KoLCharacter.canEat() )
			{
				return null;
			}

			if ( KoLCharacter.inBeecore() && ItemDatabase.unusableInBeecore( itemId ) )
			{
				return null;
			}

			if ( KoLCharacter.inNuclearAutumn() && ConsumablesDatabase.getFullness( ItemDatabase.getCanonicalName( itemId ) ) > 1 )
			{
				return null;
			}

			if ( itemId == ItemPool.BLACK_PUDDING )
			{
				return new UseLink( itemId, itemCount, "eat", "inv_eat.php?which=1&whichitem=", false );
			}

			return new UseLink( itemId, itemCount, "eat", "inv_eat.php?which=1&whichitem=" );

		case KoLConstants.CONSUME_DRINK:

			if ( !KoLCharacter.canDrink() )
			{
				return null;
			}

			if ( KoLCharacter.inBeecore() && ItemDatabase.unusableInBeecore( itemId ) )
			{
				return null;
			}

			if ( KoLCharacter.inNuclearAutumn() && ConsumablesDatabase.getInebriety( ItemDatabase.getCanonicalName( itemId ) ) > 1 )
			{
				return null;
			}

			return new UseLink( itemId, itemCount, "drink", "inv_booze.php?which=1&whichitem=" );
		
		case KoLConstants.CONSUME_FOOD_HELPER:
			if ( !KoLCharacter.canEat() )
			{
				return null;
			}
			return new UseLink( itemId, 1, "eat with", "inv_use.php?which=1&whichitem=" );
		
		case KoLConstants.CONSUME_DRINK_HELPER:
			if ( !KoLCharacter.canDrink() )
			{
				return null;
			}
			return new UseLink( itemId, 1, "drink with", "inv_use.php?which=1&whichitem=" );
		
		case KoLConstants.CONSUME_MULTIPLE:
		case KoLConstants.CONSUME_AVATAR:
		{
			int count = InventoryManager.getCount( itemId );
			int useCount = Math.min( UseItemRequest.maximumUses( itemId ), count );

			// If we are limited to 0 uses, no use link needed
			if ( useCount == 0 )
			{
				return null;
			}

			if ( KoLCharacter.inBeecore() && ItemDatabase.unusableInBeecore( itemId ) )
			{
				return null;
			}

			switch ( itemId )
			{
			case ItemPool.RUSTY_HEDGE_TRIMMERS:

				// Not inline, since the redirection to a fight
				// doesn't work ajaxified.

				return new UseLink( itemId, 1, "use", "inv_use.php?which=3&whichitem=", false );

			case ItemPool.DANCE_CARD:
				// No use link for a dance card if one is already active or another will expire in 3 turns.
				if ( TurnCounter.isCounting( "Dance Card" ) || TurnCounter.getCounters( "", 3, 3 ).length() > 0 )
				{
					return null;
				}
				break;
			}

			if ( useCount == 1 )
			{
				String page = ( consumeMethod == KoLConstants.CONSUME_MULTIPLE ) ? "3" : "1";
				return new UseLink( itemId, useCount, 
					getPotionSpeculation( "use", itemId ),
					"inv_use.php?which=" + page + "&whichitem=" );
			}

			String use = getPotionSpeculation( "use multiple", itemId );
			if ( Preferences.getBoolean( "relayUsesInlineLinks" ) )
			{
				return new UseLink( itemId, useCount, use, "#" );
			}

			return new UseLink( itemId, useCount, use, "multiuse.php?passitem=" );
		}
		
		case KoLConstants.CONSUME_FOLDER:

			// Not inline, since the redirection to a choice
			// doesn't work ajaxified.

			return new UseLink( itemId, 1, "use", "inv_use.php?which=3&whichitem=", false );

		case KoLConstants.CONSUME_SPLEEN:
		{
			int count = InventoryManager.getCount( itemId );
			int useCount = Math.min( UseItemRequest.maximumUses( itemId ), count );

			// If we are limited to 0 uses, no use link needed
			if ( useCount == 0 )
			{
				return null;
			}

			if ( KoLCharacter.inBeecore() && ItemDatabase.unusableInBeecore( itemId ) )
			{
				return null;
			}

			if ( KoLCharacter.inNuclearAutumn() && ConsumablesDatabase.getSpleenHit( ItemDatabase.getCanonicalName( itemId ) ) > 1 )
			{
				return null;
			}

			return new UseLink( itemId, useCount, 
					    getPotionSpeculation( "chew", itemId ),
					    "inv_spleen.php?whichitem=" );
		}

		case KoLConstants.CONSUME_USE:
		case KoLConstants.MESSAGE_DISPLAY:
		case KoLConstants.INFINITE_USES:

			if ( KoLCharacter.inBeecore() && ItemDatabase.unusableInBeecore( itemId ) )
			{
				return null;
			}

			switch ( itemId )
			{
			case ItemPool.LOATHING_LEGION_KNIFE:
			case ItemPool.LOATHING_LEGION_TATTOO_NEEDLE:
			case ItemPool.LOATHING_LEGION_UNIVERSAL_SCREWDRIVER:
			{
				ArrayList<UseLink> uses = new ArrayList<UseLink>();
				if ( itemId == ItemPool.LOATHING_LEGION_TATTOO_NEEDLE )
				{
					uses.add( new UseLink( itemId, 1, "use", "inv_use.php?which=3&whichitem=" ) );
				}
				else if ( itemId == ItemPool.LOATHING_LEGION_UNIVERSAL_SCREWDRIVER )
				{
					uses.add( new UseLink( itemId, 1, "untinker", "inv_use.php?which=3&whichitem=" ) );
				}
				uses.add( new UseLink( itemId, 1, "switch", "inv_use.php?which=3&switch=1&whichitem=" ) );

				if ( uses.size() == 1 )
				{
					return uses.get( 0 );
				}
				return new UsesLink( uses.toArray( new UseLink[ uses.size() ] ) );
			}

			case ItemPool.MACGUFFIN_DIARY:
			case ItemPool.ED_DIARY:
				return new UseLink( itemId, 1, "read", "diary.php?textversion=1" );

			case ItemPool.VOLCANO_MAP:
				return new UseLink( itemId, 1, "read", "volcanoisland.php?intro=1" );

			case ItemPool.ENCHANTED_BEAN:

				return  KoLCharacter.getLevel() < 10 ?
					null :
					new UseLink( itemId, "plant", "place.php?whichplace=plains&action=garbage_grounds" );

			case ItemPool.SPOOKY_SAPLING:
			case ItemPool.SPOOKY_MAP:
			case ItemPool.SPOOKY_FERTILIZER:

				if ( !InventoryManager.hasItem( ItemPool.SPOOKY_MAP ) ||
				     !InventoryManager.hasItem( ItemPool.SPOOKY_SAPLING ) ||
				     !InventoryManager.hasItem( ItemPool.SPOOKY_FERTILIZER ) )
				{
					return null;
				}

				return new UseLink( ItemPool.SPOOKY_MAP, 1, "map", "inv_use.php?which=3&whichitem=" );

			case ItemPool.FRATHOUSE_BLUEPRINTS:
			case ItemPool.RONALD_SHELTER_MAP:
			case ItemPool.GRIMACE_SHELTER_MAP:
			case ItemPool.STAFF_GUIDE:
			case ItemPool.FUDGE_WAND:
			case ItemPool.REFLECTION_OF_MAP:
			case ItemPool.CSA_FIRE_STARTING_KIT:
			case ItemPool.CEO_OFFICE_CARD:
			case ItemPool.DREADSCROLL:
			case ItemPool.RUSTY_HEDGE_TRIMMERS:
			case ItemPool.WALKIE_TALKIE:
			case ItemPool.SUSPICIOUS_ADDRESS:
			case ItemPool.CHEF_BOY_BUSINESS_CARD:
			case ItemPool.GRIMSTONE_MASK:
			case ItemPool.THUNDER_THIGH:
			case ItemPool.AQUA_BRAIN:
			case ItemPool.LIGHTNING_MILK:
			case ItemPool.SNEAKY_WRAPPING_PAPER:
			case ItemPool.BARREL_MAP:
			case ItemPool.VYKEA_INSTRUCTIONS:
			case ItemPool.TONIC_DJINN:
			case ItemPool.COW_PUNCHING_TALES:
			case ItemPool.BEAN_SLINGING_TALES:
			case ItemPool.SNAKE_OILING_TALES:
			case ItemPool.MAYO_MINDER:
			case ItemPool.NO_SPOON:
			case ItemPool.TIME_SPINNER:
			case ItemPool.WAX_GLOB:
			case ItemPool.GUMMY_MEMORY:
			case ItemPool.METAL_METEOROID:
			case ItemPool.CORKED_GENIE_BOTTLE:
			case ItemPool.GENIE_BOTTLE:
			case ItemPool.POCKET_WISH:
			case ItemPool.WAREHOUSE_KEY:

				// Not inline, since the redirection to a choice
				// doesn't work ajaxified.

			case ItemPool.ABYSSAL_BATTLE_PLANS:
			case ItemPool.CARONCH_MAP:
			case ItemPool.CRUDE_SCULPTURE:
			case ItemPool.CURSED_PIECE_OF_THIRTEEN:
			case ItemPool.DOLPHIN_WHISTLE:
			case ItemPool.ENVYFISH_EGG:
			case ItemPool.ICE_SCULPTURE:
			case ItemPool.PHOTOCOPIED_MONSTER:
			case ItemPool.RAIN_DOH_MONSTER:
			case ItemPool.SHAKING_CAMERA:
			case ItemPool.SHAKING_CRAPPY_CAMERA:
			case ItemPool.SHAKING_SKULL:
			case ItemPool.SPOOKY_PUTTY_MONSTER:
			case ItemPool.WAX_BUGBEAR:
			case ItemPool.LYNYRD_SNARE:
			case ItemPool.WHITE_PAGE:
			case ItemPool.XIBLAXIAN_HOLOTRAINING_SIMCODE:
			case ItemPool.XIBLAXIAN_POLITICAL_PRISONER:
			case ItemPool.SCREENCAPPED_MONSTER:
			case ItemPool.TIME_RESIDUE:
			case ItemPool.MEME_GENERATOR:
			case ItemPool.MEGACOPIA:
			case ItemPool.PORTABLE_PANTOGRAM:

				// Not inline, since the redirection to a fight
				// doesn't work ajaxified.

			case ItemPool.PLAINTIVE_TELEGRAM:

				// Not inline, since the redirection to an
				// adventure doesn't work ajaxified.

				return new UseLink( itemId, 1, "use", "inv_use.php?which=3&whichitem=", false );

			case ItemPool.DRUM_MACHINE:
				if ( Preferences.getInteger( "desertExploration" ) == 100 )
				{
					// This will redirect to a sandworm fight
					return new UseLink( itemId, 1, "use", "inv_use.php?which=3&whichitem=", false );
				}
				if ( InventoryManager.getCount( ItemPool.WORM_RIDING_HOOKS ) > 0 )
				{
					// This will explore the desert
					return new UseLink( itemId, 1, "wormride", "inv_use.php?which=3&whichitem=" );
				}
				// *** what happens if you try to use a drum machine with no hooks?
				return null;

			case ItemPool.GONG:
				// No use link if already under influence.
				List active = KoLConstants.activeEffects;
				if ( active.contains( FightRequest.BIRDFORM ) ||
				     active.contains( FightRequest.MOLEFORM ) )
				{
					return null;
				}
				
				// In-line use link does not work.
				return new UseLink( itemId, itemCount, "use", "inv_use.php?which=3&whichitem=", false );

			case ItemPool.COBBS_KNOB_MAP:

				if ( !InventoryManager.hasItem( ItemPool.ENCRYPTION_KEY ) )
				{
					return null;
				}

				return new UseLink( ItemPool.COBBS_KNOB_MAP, 1, "map", "inv_use.php?which=3&whichitem=" );

			case ItemPool.DINGHY_PLANS:

				if ( InventoryManager.hasItem( ItemPool.DINGY_PLANKS ) )
				{
					return new UseLink( itemId, 1, "use", "inv_use.php?which=3&whichitem=" );
				}

				return new UseLink(
					ItemPool.DINGY_PLANKS,
					1,
					"buy planks",
					"shop.php?&whichshop=generalstore&action=buyitem&quantity=1&whichrow=655",
					true );

			case ItemPool.BARLEY:
			case ItemPool.HOPS:
			case ItemPool.FANCY_BEER_BOTTLE:
			case ItemPool.FANCY_BEER_LABEL:
				return new UseLink( itemId, 1, "Let's Brew", "shop.php?whichshop=beergarden" );

			case ItemPool.WORSE_HOMES_GARDENS:
				return new UseLink( itemId, 1, "read", "shop.php?whichshop=junkmagazine" );

			case ItemPool.ODD_SILVER_COIN:
				return new UseLink( itemId, 1, "spend", "shop.php?whichshop=cindy" );

			case ItemPool.CASHEW:
				return new UseLink( itemId, 1, "trade", "shop.php?whichshop=thankshop" );

			case ItemPool.LITTLE_FIRKIN:
			case ItemPool.NORMAL_BARREL:
			case ItemPool.BIG_TUN:
			case ItemPool.WEATHERED_BARREL:
			case ItemPool.DUSTY_BARREL:
			case ItemPool.DISINTEGRATING_BARREL:
			case ItemPool.MOIST_BARREL:
			case ItemPool.ROTTING_BARREL:
			case ItemPool.MOULDERING_BARREL:
			case ItemPool.BARNACLED_BARREL:
				ArrayList<UseLink> uses = new ArrayList<UseLink>();
				uses.add( new UseLink( itemId, 1, "use", "inv_use.php?whichitem=" ) );
				uses.add( new UseLink( itemId, 1, "smash party", "inv_use.php?choice=1&whichitem=", false ) );
				return new UsesLink( uses.toArray( new UseLink[ uses.size() ] ) );

			default:

				return new UseLink( itemId, itemCount, 
					getPotionSpeculation( "use", itemId ),
					"inv_use.php?which=3&whichitem=" );
			}

		case KoLConstants.CONSUME_GUARDIAN:

			if ( KoLCharacter.inBeecore() && ItemDatabase.unusableInBeecore( itemId ) )
			{
				return null;
			}
			return new UseLink( itemId, 1, "use", "inv_use.php?which=3&whichitem=" );

		case KoLConstants.EQUIP_HAT:
		case KoLConstants.EQUIP_WEAPON:
		case KoLConstants.CONSUME_SIXGUN:
		case KoLConstants.EQUIP_OFFHAND:
		case KoLConstants.EQUIP_SHIRT:
		case KoLConstants.EQUIP_PANTS:
		case KoLConstants.EQUIP_CONTAINER:
		case KoLConstants.EQUIP_ACCESSORY:
		case KoLConstants.EQUIP_FAMILIAR:

			switch ( itemId )
			{
			case ItemPool.BATSKIN_BELT:
			case ItemPool.BONERDAGON_SKULL:
				// If we found it in a battle, take it to the
				// council to complete the quest.
				if ( combatResults )
				{
					if ( KoLCharacter.isEd() )
					{
						return new UseLink( itemId, "Amun", "council.php" );
					}
					else
					{
						return new UseLink( itemId, "council", "council.php" );
					}
				}
				break;

			case ItemPool.WORM_RIDING_HOOKS:

				// Can you even get the hooks if the desert is fully explored?
				if ( Preferences.getInteger( "desertExploration" ) == 100 )
				{
					return null;
				}
				// If you have no drum machine yet, give a link to the Oasis
				if ( InventoryManager.getCount( ItemPool.DRUM_MACHINE ) == 0 )
				{
					return new UseLink( 0, "oasis", "adventure.php?snarfblat=122" );
				}
				return new UseLink( ItemPool.DRUM_MACHINE, 1, "wormride", "inv_use.php?which=3&whichitem=" );

			case ItemPool.PIXEL_CHAIN_WHIP:
			case ItemPool.PIXEL_MORNING_STAR:
				// If we "acquire" the pixel whip upgrades in
				// the Chapel, they are autoequipped
				if ( location.startsWith( "adventure.php" ) )
				{
					return null;
				}
				break;

			case ItemPool.SCALP_OF_GORGOLOK:
			case ItemPool.ELDER_TURTLE_SHELL:
			case ItemPool.COLANDER_OF_EMERIL:
			case ItemPool.ANCIENT_SAUCEHELM:
			case ItemPool.DISCO_FRO_PICK:
			case ItemPool.EL_SOMBRERO_DE_LOPEZ:
			{
				// If we "acquire" the Nemesis hat from
				// a fight, give a link to the guild to collect
				// the reward as well as "equip" link.
				UseLink equipLink = new UseLink( itemId, itemCount,
								 getEquipmentSpeculation( "equip", itemId, -1 ),
								 "inv_equip.php?which=2&action=equip&whichitem=" );
				if ( combatResults )
				{
					ArrayList<UseLink> uses = new ArrayList<UseLink>();
					// scg = Same Class in Guild
					uses.add( new UseLink( itemId, "guild", "guild.php?place=scg" ) );
					uses.add( equipLink );
					return new UsesLink( uses.toArray( new UseLink[ uses.size() ] ) );
				}
				return equipLink;
			}

			case ItemPool.INFERNAL_SEAL_CLAW:
			case ItemPool.TURTLE_POACHER_GARTER:
			case ItemPool.SPAGHETTI_BANDOLIER:
			case ItemPool.SAUCEBLOB_BELT:
			case ItemPool.NEW_WAVE_BLING:
			case ItemPool.BELT_BUCKLE_OF_LOPEZ:
				// If we "acquire" the Nemesis accessories from
				// a fight, give a link to the guild to collect
				// the reward as well as "outfit" link.
				if ( combatResults )
				{
					ArrayList<UseLink> uses = new ArrayList<UseLink>();
					int outfit = EquipmentDatabase.getOutfitWithItem( itemId );
					// scg = Same Class in Guild
					uses.add( new UseLink( itemId, "guild", "guild.php?place=scg" ) );
					uses.add( new UseLink( itemId, itemCount, "outfit", "inv_equip.php?action=outfit&which=2&whichoutfit=" + outfit ) );
					return new UsesLink( uses.toArray( new UseLink[ uses.size() ] ) );
				}
				break;

			case ItemPool.SPELUNKY_SPRING_BOOTS:
			case ItemPool.SPELUNKY_SPIKED_BOOTS:
				// Spelunky "accessories" need a single "equip"
				// link which goes to slot 1
				return new UseLink( itemId, itemCount, "equip", "inv_equip.php?which=2&action=equip&slot=1&whichitem=" );

			case ItemPool.HEIMZ_BEANS:
			case ItemPool.TESLA_BEANS:
			case ItemPool.MIXED_BEANS:
			case ItemPool.HELLFIRE_BEANS:
			case ItemPool.FRIGID_BEANS:
			case ItemPool.BLACKEST_EYED_PEAS:
			case ItemPool.STINKBEANS:
			case ItemPool.PORK_N_BEANS:
			case ItemPool.PREMIUM_BEANS:
			{
				UseLink equipLink = new UseLink( itemId, itemCount,
								 getEquipmentSpeculation( "equip", itemId, -1 ),
								 "inv_equip.php?which=2&action=equip&whichitem=" );
				// inv_use.php?pwd&which=f-1&whichitem=xxx
				UseLink plateLink = new UseLink( itemId, itemCount,
								 "plate",
								 "inv_use.php?which=f-1&whichitem=" );
				ArrayList<UseLink> uses = new ArrayList<UseLink>();
				uses.add( equipLink );
				uses.add( plateLink );
				return new UsesLink( uses.toArray( new UseLink[ uses.size() ] ) );
			}

			case ItemPool.CODPIECE:
			{
				UseLink equipLink = new UseLink( itemId, itemCount,
								 getEquipmentSpeculation( "equip", itemId, -1 ),
								 "inv_equip.php?which=2&action=equip&whichitem=" );
				// inv_use.php?pwd&which=f-1&whichitem=xxx
				UseLink wringOutLink = new UseLink( itemId, itemCount,
								 "wring out",
								 "inv_use.php?which=f-1&whichitem=" );
				ArrayList<UseLink> uses = new ArrayList<UseLink>();
				uses.add( equipLink );
				uses.add( wringOutLink );
				return new UsesLink( uses.toArray( new UseLink[ uses.size() ] ) );
			}

			case ItemPool.BASS_CLARINET:
			{
				UseLink equipLink = new UseLink( itemId, itemCount,
								 getEquipmentSpeculation( "equip", itemId, -1 ),
								 "inv_equip.php?which=2&action=equip&whichitem=" );
				// inv_use.php?pwd&which=f-1&whichitem=xxx
				UseLink drainLink = new UseLink( itemId, itemCount,
								 "drain spit",
								 "inv_use.php?which=f-1&whichitem=" );
				ArrayList<UseLink> uses = new ArrayList<UseLink>();
				uses.add( equipLink );
				uses.add( drainLink );
				return new UsesLink( uses.toArray( new UseLink[ uses.size() ] ) );
			}

			case ItemPool.FISH_HATCHET:
			{
				UseLink equipLink = new UseLink( itemId, itemCount,
								 getEquipmentSpeculation( "equip", itemId, -1 ),
								 "inv_equip.php?which=2&action=equip&whichitem=" );
				// inv_use.php?pwd&which=f-1&whichitem=xxx
				UseLink useLink = new UseLink( itemId, itemCount,
								 "use",
								 "inv_use.php?which=f-1&whichitem=" );
				ArrayList<UseLink> uses = new ArrayList<UseLink>();
				uses.add( equipLink );
				uses.add( useLink );
				return new UsesLink( uses.toArray( new UseLink[ uses.size() ] ) );
			}
			}

			// Don't offer an "equip" link for weapons or offhands
			// in Fistcore or Axecore
			if ( ( consumeMethod == KoLConstants.EQUIP_WEAPON || consumeMethod == KoLConstants.EQUIP_OFFHAND ) &&
			     ( KoLCharacter.inFistcore() || KoLCharacter.inAxecore() ) )
			{
				return null;
			}

			int outfit = EquipmentDatabase.getOutfitWithItem( itemId );

			ArrayList<UseLink> uses = new ArrayList<UseLink>();
			
			if ( outfit != -1 && EquipmentManager.hasOutfit( outfit ) )
			{
				uses.add( new UseLink( itemId, itemCount, "outfit", "inv_equip.php?action=outfit&which=2&whichoutfit=" + outfit ) );
			}
			
			if ( consumeMethod == KoLConstants.EQUIP_ACCESSORY &&
			     !EquipmentManager.getEquipment( EquipmentManager.ACCESSORY1 ).equals( EquipmentRequest.UNEQUIP ) && 
			     !EquipmentManager.getEquipment( EquipmentManager.ACCESSORY2 ).equals( EquipmentRequest.UNEQUIP ) && 
			     !EquipmentManager.getEquipment( EquipmentManager.ACCESSORY3 ).equals( EquipmentRequest.UNEQUIP ) )
			{
				uses.add( new UseLink( itemId, itemCount,
					getEquipmentSpeculation( "acc1", itemId,  EquipmentManager.ACCESSORY1 ),
					"inv_equip.php?which=2&action=equip&slot=1&whichitem=" ) );
				uses.add( new UseLink( itemId, itemCount,
					getEquipmentSpeculation( "acc2", itemId,  EquipmentManager.ACCESSORY2 ),
					 "inv_equip.php?which=2&action=equip&slot=2&whichitem=" ) );
				uses.add( new UseLink( itemId, itemCount,
					getEquipmentSpeculation( "acc3", itemId,  EquipmentManager.ACCESSORY3 ),
					 "inv_equip.php?which=2&action=equip&slot=3&whichitem=" ) );
			}
			else if ( consumeMethod == KoLConstants.CONSUME_SIXGUN )
			{
				// Only as WOL class
				if ( KoLCharacter.getClassType() != KoLCharacter.COWPUNCHER && KoLCharacter.getClassType() != KoLCharacter.BEANSLINGER &&
					KoLCharacter.getClassType() != KoLCharacter.SNAKE_OILER )
				{
					return null;
				}
				uses.add( new UseLink( itemId, itemCount,
					getEquipmentSpeculation( "holster", itemId, -1 ),
					"inventory.php?which=2&action=holster&whichitem=", false ) );
			}
			else
			{
				uses.add( new UseLink( itemId, itemCount,
					getEquipmentSpeculation( "equip", itemId, -1 ),
					"inv_equip.php?which=2&action=equip&whichitem=" ) );

				// Quietly, stealthily, you reach out and steal the pants from your
				// unsuspecting self, and fade back into the mazy passages of the
				// Sleazy Back Alley before you notice what has happened.
				//
				// Then you make your way back out of the Alley, clutching your pants
				// triumphantly and trying really hard not to think about how oddly
				// chilly it has suddenly become.

				if ( consumeMethod == KoLConstants.EQUIP_PANTS &&
				     text.contains( "steal the pants from your unsuspecting self" ) )
				{
					uses.add( new UseLink( itemId, "guild", "guild.php?place=challenge" ) );
				}
			}

			if ( consumeMethod == KoLConstants.EQUIP_WEAPON &&
				EquipmentDatabase.getHands( itemId ) == 1 &&
				EquipmentDatabase.getHands( EquipmentManager.getEquipment( EquipmentManager.WEAPON ).getItemId() ) == 1 &&
				KoLCharacter.hasSkill( "Double-Fisted Skull Smashing" ) )
			{
				uses.add( new UseLink( itemId, itemCount,
					getEquipmentSpeculation( "offhand", itemId, EquipmentManager.OFFHAND ),
					"inv_equip.php?which=2&action=dualwield&whichitem=" ) );
			}
			
			if ( consumeMethod != KoLConstants.EQUIP_FAMILIAR &&
				KoLCharacter.getFamiliar().canEquip( ItemPool.get( itemId, 1 ) ) )
			{
				uses.add( new UseLink( itemId, itemCount,
					getEquipmentSpeculation( "familiar", itemId, EquipmentManager.FAMILIAR ),
					"inv_equip.php?which=2&action=hatrack&whichitem=" ) );
			}

			switch ( itemId )
			{
			case ItemPool.LOATHING_LEGION_MANY_PURPOSE_HOOK:
			case ItemPool.LOATHING_LEGION_MOONDIAL:
			case ItemPool.LOATHING_LEGION_NECKTIE:
			case ItemPool.LOATHING_LEGION_ELECTRIC_KNIFE:
			case ItemPool.LOATHING_LEGION_CORKSCREW:
			case ItemPool.LOATHING_LEGION_CAN_OPENER:
			case ItemPool.LOATHING_LEGION_CHAINSAW:
			case ItemPool.LOATHING_LEGION_ROLLERBLADES:
			case ItemPool.LOATHING_LEGION_FLAMETHROWER:
			case ItemPool.LOATHING_LEGION_DEFIBRILLATOR:
			case ItemPool.LOATHING_LEGION_DOUBLE_PRISM:
			case ItemPool.LOATHING_LEGION_TAPE_MEASURE:
			case ItemPool.LOATHING_LEGION_KITCHEN_SINK:
			case ItemPool.LOATHING_LEGION_ABACUS:
			case ItemPool.LOATHING_LEGION_HELICOPTER:
			case ItemPool.LOATHING_LEGION_PIZZA_STONE:
			case ItemPool.LOATHING_LEGION_HAMMER:
				uses.add( new UseLink( itemId, 1, "switch", "inv_use.php?which=3&switch=1&whichitem=" ) );
				break;

			case ItemPool.INSULT_PUPPET:
			case ItemPool.OBSERVATIONAL_GLASSES:
			case ItemPool.COMEDY_PROP:
				uses.add( new UseLink( itemId, itemCount, "visit mourn", "pandamonium.php?action=mourn&whichitem=" ) );
				break;
			}

			if ( uses.size() == 1 )
			{
				return uses.get( 0 );
			}
			else
			{
				return new UsesLink( uses.toArray( new UseLink[ uses.size() ] ) );
			}

		case KoLConstants.CONSUME_ZAP:

			return new UseLink( itemId, itemCount, "zap", "wand.php?whichwand=" );
		}

		return null;
	}
	
	private static int equipSequence = 0;
	
	private static final String getSpeculation( String label, Modifiers mods )
	{
		String id = "whatif" + UseLinkDecorator.equipSequence++;
		String table = SpeculateCommand.getHTML( mods, "id='" + id + "' style='background-color: white; visibility: hidden; position: absolute; right: 0px; top: 1.2em;'" );
		if ( table == null ) return label;
		return "<span style='position: relative;' onMouseOver=\"document.getElementById('" + id + "').style.visibility='visible';\" onMouseOut=\"document.getElementById('" + id + "').style.visibility='hidden';\">" + table + label + "</span>";
	}

	public static final String getEquipmentSpeculation( String label, int itemId, int slot )
	{
		if ( slot == -1 )
		{
			slot = EquipmentRequest.chooseEquipmentSlot( itemId );
		}
		Speculation spec = new Speculation();
		spec.equip( slot, ItemPool.get( itemId, 1 ) );
		Modifiers mods = spec.calculate();
		return getSpeculation( label, mods );
	}

	private static final String getPotionSpeculation( String label, int itemId )
	{
		Modifiers mods = Modifiers.getItemModifiers( itemId );
		if ( mods == null ) return label;
		String effect = mods.getString( Modifiers.EFFECT );
		if ( effect.equals( "" ) ) return label;
		int duration = (int)mods.get( Modifiers.EFFECT_DURATION );
		int effectId = EffectDatabase.getEffectId( effect );
		Speculation spec = new Speculation();
		spec.addEffect( EffectPool.get( effectId, Math.max( 1, duration ) ) );
		mods = spec.calculate();
		mods.set( Modifiers.EFFECT, effect );
		if ( duration > 0 )
		{
			mods.set( Modifiers.EFFECT_DURATION, (float)duration );
		}
		return getSpeculation( label, mods );
	}

	private static final UseLink getNavigationLink( int itemId, String location )
	{
		String useType = null;
		String useLocation = null;
		boolean combatResults = location.startsWith( "fight.php" );

		switch ( itemId )
		{
		// Shops
		case ItemPool.FRESHWATER_FISHBONE:
			useType = "assemble";
			useLocation = "shop.php?whichshop=fishbones";
			break;

		case ItemPool.TOPIARY_NUGGLET:
			useType = "sculpt";
			useLocation = "shop.php?whichshop=topiary";
			break;

		case ItemPool.TOXIC_GLOBULE:
			useType = "do science";
			useLocation = "shop.php?whichshop=toxic";
			break;

		case ItemPool.ROSE:
		case ItemPool.WHITE_TULIP:
		case ItemPool.RED_TULIP:
		case ItemPool.BLUE_TULIP:
			useType = "trade in";
			useLocation = "shop.php?whichshop=flowertradein";
			break;

		case ItemPool.FAT_LOOT_TOKEN:
			useType = String.valueOf( InventoryManager.getCount( ItemPool.FAT_LOOT_TOKEN ) );
			useLocation = "shop.php?whichshop=damachine";
			break;
			
		// Subject 37 File goes to Cell #37
		case ItemPool.SUBJECT_37_FILE:
			useType = "cell #37";
			useLocation = "cobbsknob.php?level=3&action=cell37";
			break;

		// Guild quest items go to guild chief
		case ItemPool.BIG_KNOB_SAUSAGE:
		case ItemPool.EXORCISED_SANDWICH:
			useType = "guild";
			useLocation = "guild.php?place=challenge";
			break;

		case ItemPool.LOATHING_LEGION_JACKHAMMER:
			useType = "switch";
			useLocation = "inv_use.php?which=3&switch=1&whichitem=";
			break;

		// Game Grid tokens get a link to the arcade.

		case ItemPool.GG_TOKEN:

			useType = "arcade";
			useLocation = "place.php?whichplace=arcade";
			break;

		// Game Grid tickets get a link to the arcade redemption counter.

		case ItemPool.GG_TICKET:

			useType = "redeem";
			useLocation = "shop.php?whichshop=arcade";
			break;

		// Soft green echo eyedrop antidote gets an uneffect link

		case ItemPool.REMEDY:
		case ItemPool.ANCIENT_CURE_ALL:

			useType = "use";
			useLocation = "uneffect.php";
			break;

		// Strange leaflet gets a quick 'read' link which sends you
		// to the leaflet completion page.

		case ItemPool.STRANGE_LEAFLET:

			useType = "read";
			useLocation = "leaflet.php?action=auto";
			break;

		// You want to give the rusty screwdriver to the Untinker, so
		// make it easy.

		case ItemPool.RUSTY_SCREWDRIVER:

			useType = "visit untinker";
			useLocation = "place.php?whichplace=forestvillage&action=fv_untinker";
			break;

		// Hedge maze puzzle and hedge maze key have a link to the maze
		// for easy access.

		case ItemPool.HEDGE_KEY:
		case ItemPool.PUZZLE_PIECE:

			useType = "maze";
			useLocation = "hedgepuzzle.php";
			break;

		// Pixels have handy links indicating how many white pixels are
		// present in the player's inventory.

		case ItemPool.WHITE_PIXEL:
		case ItemPool.RED_PIXEL:
		case ItemPool.GREEN_PIXEL:
		case ItemPool.BLUE_PIXEL:

			int whiteCount = CreateItemRequest.getInstance( ItemPool.WHITE_PIXEL ).getQuantityPossible() + InventoryManager.getCount( ItemPool.WHITE_PIXEL );
			useType = whiteCount + " white";
			useLocation = "place.php?whichplace=forestvillage&action=fv_mystic";
			break;

		// Special handling for star charts, lines, and stars, where
		// KoLmafia shows you how many of each you have.

		case ItemPool.STAR_CHART:
		case ItemPool.STAR:
		case ItemPool.LINE:

			useType = InventoryManager.getCount( ItemPool.STAR_CHART ) + "," + InventoryManager.getCount( ItemPool.STAR ) + "," + InventoryManager.getCount( ItemPool.LINE );
			useLocation = "shop.php?whichshop=starchart";
			break;

		// Worthless items and the hermit permit get a link to the hermit.

		case ItemPool.WORTHLESS_TRINKET:
		case ItemPool.WORTHLESS_GEWGAW:
		case ItemPool.WORTHLESS_KNICK_KNACK:
		case ItemPool.HERMIT_PERMIT:

			useType = "hermit";
			useLocation = "hermit.php";
			break;

		// The different kinds of ores will only have a link if they're
		// the ones applicable to the trapper quest.

		case ItemPool.LINOLEUM_ORE:
		case ItemPool.ASBESTOS_ORE:
		case ItemPool.CHROME_ORE:
		case ItemPool.LUMP_OF_COAL:

			if ( location.startsWith( "dwarffactory.php" ) )
			{
				useType = String.valueOf( InventoryManager.getCount( itemId ) );
				useLocation = "dwarfcontraption.php";
				break;
			}

			if ( itemId != ItemDatabase.getItemId( Preferences.getString( "trapperOre" ) ) )
			{
				return null;
			}

			useType = String.valueOf( InventoryManager.getCount( itemId ) );
			useLocation = "place.php?whichplace=mclargehuge&action=trappercabin";
			break;

		case ItemPool.GROARS_FUR:
		case ItemPool.WINGED_YETI_FUR:

			useType = "trapper";
			useLocation = "place.php?whichplace=mclargehuge&action=trappercabin";
			break;

		case ItemPool.FRAUDWORT:
		case ItemPool.SHYSTERWEED:
		case ItemPool.SWINDLEBLOSSOM:

			if ( InventoryManager.getCount( ItemPool.FRAUDWORT ) < 3 ||
			     InventoryManager.getCount( ItemPool.SHYSTERWEED ) < 3 ||
			     InventoryManager.getCount( ItemPool.SWINDLEBLOSSOM ) < 3 )
			{
				return null;
			}

			useType = "galaktik";
			useLocation = "shop.php?whichshop=doc";
			break;

		// Disintegrating sheet music gets a link which lets you sing it
		// to yourself. We'll call it "sing" for now.

		case ItemPool.SHEET_MUSIC:

			useType = "sing";
			useLocation = "curse.php?action=use&targetplayer=" + KoLCharacter.getPlayerId() + "&whichitem=";
			break;

		// Link which uses the plans when you acquire the planks.

		case ItemPool.DINGY_PLANKS:

			if ( !InventoryManager.hasItem( ItemPool.DINGHY_PLANS ) )
			{
				return null;
			}

			useType = "plans";
			useLocation = "inv_use.php?which=3&whichitem=";
			itemId = ItemPool.DINGHY_PLANS;
			break;

		// Link which uses the Knob map when you get the encryption key.

		case ItemPool.ENCRYPTION_KEY:

			if ( !InventoryManager.hasItem( ItemPool.COBBS_KNOB_MAP ) )
			{
				return null;
			}

			useType = "use map";
			useLocation = "inv_use.php?which=3&whichitem=";
			itemId = ItemPool.COBBS_KNOB_MAP;
			break;

		// Link to the guild upon completion of the Citadel quest.

		case ItemPool.CITADEL_SATCHEL:
		case ItemPool.THICK_PADDED_ENVELOPE:

			useType = "guild";
			useLocation = "guild.php?place=paco";
			break;
		
		// Link to the guild when receiving guild quest items.
		
		case ItemPool.FERNSWARTHYS_KEY:
			// ...except that the guild gives you the key again
			if ( location.startsWith( "guild.php" ) )
			{
				useType = "ruins";
				useLocation = "fernruin.php";
				break;
			}
			/*FALLTHRU*/
		case ItemPool.DUSTY_BOOK:
			useType = "guild";
			useLocation = "guild.php?place=ocg";
			break;

		// Link to the untinker if you find an abridged dictionary.

		case ItemPool.ABRIDGED:

			useType = "untinker";
			useLocation = "place.php?whichplace=forestvillage&action=fv_untinker";
			break;

		// Link to the chasm if you just untinkered a dictionary.

		case ItemPool.BRIDGE:
		case ItemPool.DICTIONARY:
		case ItemPool.MORNINGWOOD_PLANK:
		case ItemPool.HARDWOOD_PLANK:
		case ItemPool.WEIRDWOOD_PLANK:
		case ItemPool.THICK_CAULK:
		case ItemPool.LONG_SCREW:
		case ItemPool.BUTT_JOINT:
		case ItemPool.SNOW_BOARDS:

			int urlEnd = OrcChasmRequest.getChasmProgress();
			if ( urlEnd == 30 )
			{
				break;
			}

			useType = "chasm";
			useLocation = "place.php?whichplace=orc_chasm&action=bridge" + urlEnd;
			break;

		// Link to the frat house if you acquired a Spanish Fly

		case ItemPool.SPANISH_FLY:

			useType = String.valueOf( InventoryManager.getCount( itemId ) );
			useLocation = "adventure.php?snarfblat=27";
			break;

		// Link to Big Brother if you pick up a sand dollar

		case ItemPool.SAND_DOLLAR:

			useType = String.valueOf( InventoryManager.getCount( itemId ) );
			useLocation = "monkeycastle.php?who=2";
			break;

		// Link to the Old Man if you buy the damp old boot

		case ItemPool.DAMP_OLD_BOOT:

			useType = "old man";
			useLocation = "place.php?whichplace=sea_oldman&action=oldman_oldman";
			break;

		case ItemPool.GUNPOWDER:
			useType = String.valueOf( InventoryManager.getCount( itemId ) );
			useLocation = IslandRequest.getPyroURL();
			break;

		case ItemPool.TOWEL:
			useType = "fold";
			useLocation = "inv_use.php?which=3&whichitem=";
			break;

		case ItemPool.GOLD_BOWLING_BALL:
		case ItemPool.REALLY_DENSE_MEAT_STACK:
		case ItemPool.SCARAB_BEETLE_STATUETTE:
			if ( !combatResults ) break;
			/*FALLTHRU*/
		case ItemPool.HOLY_MACGUFFIN:
		case ItemPool.ED_HOLY_MACGUFFIN:

			if ( KoLCharacter.isEd() )
			{
				useType = "Amun";
			}
			else
			{
				useType = "council";
			}
			useLocation = "council.php";
			break;

		// Link to the Pretentious Artist when you find his last tool

		case ItemPool.PRETENTIOUS_PAINTBRUSH:
		case ItemPool.PRETENTIOUS_PALETTE:
		case ItemPool.PRETENTIOUS_PAIL:

			if ( !InventoryManager.hasItem( ItemPool.PRETENTIOUS_PAINTBRUSH ) ||
			     !InventoryManager.hasItem( ItemPool.PRETENTIOUS_PALETTE ) ||
			     !InventoryManager.hasItem( ItemPool.PRETENTIOUS_PAIL ) )
			{
				return null;
			}

			useType = "artist";
			useLocation = "place.php?whichplace=town_wrong&action=townwrong_artist_quest";
			break;

		case ItemPool.MOLYBDENUM_MAGNET:
		case ItemPool.MOLYBDENUM_HAMMER:
		case ItemPool.MOLYBDENUM_PLIERS:
		case ItemPool.MOLYBDENUM_SCREWDRIVER:
		case ItemPool.MOLYBDENUM_WRENCH:

			useType = "yossarian";
			useLocation = "bigisland.php?action=junkman";
			break;

		case ItemPool.FILTHWORM_QUEEN_HEART:

			useType = "stand";
			useLocation = "bigisland.php?place=orchard&action=stand";
			break;

		case ItemPool.EMPTY_AGUA_DE_VIDA_BOTTLE:

			useType = "gaze";
			useLocation = "place.php?whichplace=memories";
			break;

		case ItemPool.SUGAR_SHEET:

			useType = "fold";
			useLocation = "shop.php?whichshop=sugarsheets";
			break;

		// Link to the kegger if you acquired a phone number. That's
		// not useful, but having the item count in the link is

		case ItemPool.ORQUETTES_PHONE_NUMBER:

			useType = String.valueOf( InventoryManager.getCount( itemId ) );
			useLocation = "adventure.php?snarfblat=231";
			break;

		case ItemPool.FORGED_ID_DOCUMENTS:

			useType = "vacation";
			useLocation = "adventure.php?snarfblat=355";
			break;

		case ItemPool.BUS_PASS:
		case ItemPool.IMP_AIR:

			useType = String.valueOf( InventoryManager.getCount( itemId ) );
			useLocation = "pandamonium.php?action=moan";
			break;

		case ItemPool.HACIENDA_KEY:

			useType = String.valueOf( InventoryManager.getCount( itemId ) );
			useLocation = "volcanoisland.php?action=tniat&pwd=" + GenericRequest.passwordHash;
			break;

		case ItemPool.NOSTRIL_OF_THE_SERPENT:
			if ( !InventoryManager.hasItem( ItemPool.STONE_WOOL ) )
			{
				return null;
			}

			itemId = ItemPool.STONE_WOOL;
			useType = "stone wool";
			useLocation = "inv_use.php?which=3&whichitem=";
			break;

		case ItemPool.MOSS_COVERED_STONE_SPHERE:
			useType = "use sphere";
			useLocation = "adventure.php?snarfblat=346";
			break;

		case ItemPool.DRIPPING_STONE_SPHERE:
			useType = "use sphere";
			useLocation = "adventure.php?snarfblat=347";
			break;

		case ItemPool.CRACKLING_STONE_SPHERE:
			useType = "use sphere";
			useLocation = "adventure.php?snarfblat=348";
			break;

		case ItemPool.SCORCHED_STONE_SPHERE:
			useType = "use sphere";
			useLocation = "adventure.php?snarfblat=349";
			break;

		case ItemPool.STONE_ROSE:
			useType = "gnasir";
			useLocation = "place.php?whichplace=desertbeach&action=db_gnasir";
			break;

		case ItemPool.WORM_RIDING_MANUAL_PAGE:
		{
			int count = InventoryManager.getCount( itemId );
			return count < 15 ?
				new UseLink( itemId, count ) :
				new UseLink( itemId, count, "gnasir", "place.php?whichplace=desertbeach&action=db_gnasir" );
		}

		case ItemPool.FIRST_PIZZA:
		case ItemPool.LACROSSE_STICK:
		case ItemPool.EYE_OF_THE_STARS:
		case ItemPool.STANKARA_STONE:
		case ItemPool.MURPHYS_FLAG:
		case ItemPool.SHIELD_OF_BROOK:
			useType = "copperhead club";
			useLocation = "adventure.php?snarfblat=383";
			break;

		case ItemPool.GOLD_PIECE:
			return new UseLink( itemId, InventoryManager.getCount( itemId ) );

		case ItemPool.SPOOKYRAVEN_NECKLACE:

			useType = "talk to Lady Spookyraven";
			useLocation = "place.php?whichplace=manor1&action=manor1_ladys";
			break;

		case ItemPool.POWDER_PUFF:
		case ItemPool.FINEST_GOWN:
		case ItemPool.DANCING_SHOES:

			if ( !InventoryManager.hasItem( ItemPool.POWDER_PUFF ) ||
			     !InventoryManager.hasItem( ItemPool.FINEST_GOWN ) ||
			     !InventoryManager.hasItem( ItemPool.DANCING_SHOES ) )
			{
				return null;
			}

			useType = "talk to Lady Spookyraven";
			useLocation = "place.php?whichplace=manor2&action=manor2_ladys";
			break;

		case ItemPool.BABY_GHOSTS:

			useType = "talk to Lady Spookyraven";
			useLocation = "place.php?whichplace=manor3&action=manor3_ladys";
			break;

		case ItemPool.CRUMBLING_WHEEL:
		{
			int count1 = InventoryManager.getCount( itemId );
			int count2 = InventoryManager.getCount( ItemPool.TOMB_RATCHET );
			useType = String.valueOf( count1 ) + "+" + String.valueOf( count2 );
			return !Preferences.getBoolean( "controlRoomUnlock" ) ?
				new UseLink( itemId, count1, useType, "javascript:return false;" ) :
				new UseLink( itemId, count1, useType, "place.php?whichplace=pyramid&action=pyramid_control" );
		}

		case ItemPool.TOMB_RATCHET:
		{
			int count1 = InventoryManager.getCount( itemId );
			int count2 = InventoryManager.getCount( ItemPool.CRUMBLING_WHEEL );
			useType = String.valueOf( count2 ) + "+" + String.valueOf( count1 );
			return !Preferences.getBoolean( "controlRoomUnlock" ) ?
				new UseLink( itemId, count1, useType, "javascript:return false;" ) :
				new UseLink( itemId, count1, useType, "place.php?whichplace=pyramid&action=pyramid_control" );
		}

		case ItemPool.PACK_OF_SMOKES:
		{
			int count = InventoryManager.getCount( itemId );
			return count < 10 ?
				new UseLink( itemId, count ) :
				new UseLink( itemId, count, "radio", "place.php?whichplace=airport_spooky&action=airport2_radio" );
		}

		case ItemPool.EXPERIMENTAL_SERUM_P00:
		{
			int count = InventoryManager.getCount( itemId );
			return count < 5 && QuestDatabase.isQuestLaterThan( Quest.SERUM, QuestDatabase.UNSTARTED ) ?
				new UseLink( itemId, count ) :
				new UseLink( itemId, count, "radio", "place.php?whichplace=airport_spooky&action=airport2_radio" );
		}

		case ItemPool.MEATSMITH_CHECK:
			return new UseLink( itemId, 1, "visit meatsmith", "shop.php?whichshop=meatsmith" );

		case ItemPool.NO_HANDED_PIE:
			return new UseLink( itemId, 1, "visit armorer", "shop.php?whichshop=armory" );

		case ItemPool.BACON:
			int baconcount = InventoryManager.getCount( itemId );
			useType = "spend (" + baconcount + ")";
			useLocation = "shop.php?whichshop=bacon";
			break;

		case ItemPool.RAD:
			int radcount = InventoryManager.getCount( itemId );
			useType = "mutate (" + radcount + ")";
			useLocation = "shop.php?whichshop=mutate";
			break;

		case ItemPool.CASHEW:
			useType = "thanksgiving";
			useLocation = "shop.php?whichshop=thankshop";
			break;

		case ItemPool.SPANT_CHITIN:
		case ItemPool.SPANT_TENDON:
			useType = "assemble";
			useLocation = "shop.php?whichshop=spant";
			break;

		default:

		}

		if ( useType == null || useLocation == null )
		{
			return null;
		}

		return new UseLink( itemId, useType, useLocation );
	}

	public static class UseLink
	{
		private int itemId;
		private int itemCount;
		private String useType;
		private String useLocation;
		private boolean inline;
		
		protected UseLink()
		{
		}

		public UseLink( String useType, String useLocation )
		{
			this( -1, 0, useType, useLocation, false );
		}

		public UseLink( int itemId, String useType, String useLocation )
		{
			this( itemId, 1, useType, useLocation );
		}

		public UseLink( int itemId, int itemCount, String useLocation )
		{
			this( itemId, itemCount, String.valueOf( itemCount ), useLocation );
		}

		public UseLink( int itemId, int itemCount, String useType, String useLocation )
		{
			this( itemId, itemCount, useType, useLocation, useLocation.startsWith( "inv" ) );
		}

		public UseLink( int itemId, int itemCount )
		{
			// This is just a counter
			this( itemId, itemCount, "javascript:return false;" );
		}

		public UseLink( int itemId, int itemCount, String useType, String useLocation, boolean inline )
		{
			this.itemId = itemId;
			this.itemCount = itemCount;
			this.useType = useType;
			this.useLocation = useLocation;
			this.inline = inline;

			if ( this.useLocation.endsWith( "=" ) )
			{
				this.useLocation += this.itemId;
			}

			if ( this.useLocation.contains( "?" ) && !this.useLocation.contains( "phash=" ) &&
			     // It's not harmful to include the password hash
			     // when it is unnecessary, but it is not pretty
			     !this.useLocation.startsWith( "adventure.php" ) &&
			     !this.useLocation.startsWith( "place.php" ) &&
			     !this.useLocation.startsWith( "council.php" ) &&
			     !this.useLocation.startsWith( "guild.php" ) &&
			     !this.useLocation.startsWith( "wand.php" ) &&
			     !this.useLocation.startsWith( "diary.php" ) &&
			     !this.useLocation.startsWith( "volcanoisland.php" ) &&
			     !this.useLocation.startsWith( "cobbsknob.php" ) )
			{
				this.useLocation += "&pwd=" + GenericRequest.passwordHash;
			}
		}

		public int getItemId()
		{
			return this.itemId;
		}

		public int getItemCount()
		{
			return this.itemCount;
		}

		public String getUseType()
		{
			return this.useType;
		}

		public String getUseLocation()
		{
			return this.useLocation;
		}

		public boolean showInline()
		{
			return this.inline && Preferences.getBoolean( "relayUsesInlineLinks" );
		}

		public String getItemHTML()
		{
			if ( this.useLocation.equals( "#" ) )
			{
				return "<font size=1>[<a href=\"javascript:" + "multiUse('multiuse.php'," + this.itemId + "," + this.itemCount + ");void(0);\">" + this.useType + "</a>]</font>";
			}

			if ( !this.showInline() )
			{
				return "<font size=1>[<a href=\"" + this.useLocation + "\">" + this.useType + "</a>]</font>";
			}

			String[] pieces = this.useLocation.split( "\\?" );

			return "<font size=1>[<a href=\"javascript:" + "singleUse('" + pieces[ 0 ].trim() + "','" + pieces[ 1 ].trim() + "&ajax=1');void(0);\">" + this.useType + "</a>]</font>";
		}
	}
	
	public static class UsesLink
		extends UseLink
	{
		private final UseLink[] links;
		
		public UsesLink( UseLink[] links )
		{
			this.links = links;
		}
		
		@Override
		public String getItemHTML()
		{
			StringBuilder buf = new StringBuilder();
			for ( int i = 0; i < this.links.length; ++i )
			{
				if ( i > 0 ) buf.append( "&nbsp;" );
				buf.append( this.links[ i ].getItemHTML() );
			}
			return buf.toString();
		}
	}
}
